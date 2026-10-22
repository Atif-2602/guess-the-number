/* ============================================================
   Guess the Number - browser logic

   All game rules live in the Java backend. This file only sends
   guesses and paints the result.
   ============================================================ */

(() => {
  "use strict";

  const NEW_GAME_URL = "api/new-game";
  const GUESS_URL = "api/guess";
  const BEST_KEY = "guess-the-number.best";

  // GitHub Pages (and file://) can only serve files, so the browser runs the
  // game rules itself there. Everywhere else the Java server is used.
  const STATIC_HOST =
    location.protocol === "file:" || /\.github\.io$/i.test(location.hostname);

  const el = {
    attempts: document.getElementById("attempts"),
    range: document.getElementById("range"),
    best: document.getElementById("best"),
    form: document.getElementById("guess-form"),
    input: document.getElementById("guess-input"),
    guessBtn: document.getElementById("guess-btn"),
    feedback: document.getElementById("feedback"),
    history: document.getElementById("history"),
    newGame: document.getElementById("new-game"),
    howTo: document.getElementById("how-to"),
    howToBtn: document.getElementById("how-to-btn"),
  };

  const state = {
    gameId: null,
    attempts: 0,
    min: 1,
    max: 100,
    low: 1,
    high: 100,
    finished: false,
    busy: false,
    best: readBest(),
    // "server" = Java backend, "local" = static build (GitHub Pages / file://)
    mode: STATIC_HOST ? "local" : "server",
    secret: null,
  };

  /* ------------------------------------------------------------
     Server calls
     ------------------------------------------------------------ */

  class ApiError extends Error {
    constructor(message, status) {
      super(message);
      this.name = "ApiError";
      this.status = status;
    }
  }

  async function postJson(url, payload) {
    let response;
    try {
      response = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
    } catch (error) {
      throw new Error("network");
    }

    const text = await response.text();
    let data = {};
    try {
      data = text ? JSON.parse(text) : {};
    } catch (error) {
      data = {};
    }

    if (!response.ok) {
      throw new ApiError(data.message || "Server error (" + response.status + ").", response.status);
    }
    return data;
  }

  /* ------------------------------------------------------------
     Static / offline mode
     ------------------------------------------------------------

     GitHub Pages can only host static files, so the Java server is not
     available there. In that case the exact same game rules run here in
     the browser. The UI does not change.
     ------------------------------------------------------------ */

  async function loadNewGame() {
    if (state.mode === "local") {
      return localNewGame();
    }

    try {
      const response = await fetch(NEW_GAME_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: "{}",
      });
      const type = response.headers.get("Content-Type") || "";

      // A static host answers with an HTML error page instead of our JSON API.
      if (!response.ok || !type.includes("application/json")) {
        throw new Error("static-host");
      }
      return await response.json();
    } catch (error) {
      console.info("Java API not available, starting browser-only game.");
      state.mode = "local";
      return localNewGame();
    }
  }

  function localNewGame() {
    state.secret = Math.floor(Math.random() * (state.max - state.min + 1)) + state.min;
    return {
      gameId: "local",
      min: state.min,
      max: state.max,
      attempts: 0,
      finished: false,
    };
  }

  function localGuess(guess) {
    const attempts = state.attempts + 1;

    if (guess < state.secret) {
      return {
        status: "TOO_LOW",
        message: "Too small!  Try a bigger number.",
        attempts,
        finished: false,
      };
    }
    if (guess > state.secret) {
      return {
        status: "TOO_HIGH",
        message: "Too large!  Try a smaller number.",
        attempts,
        finished: false,
      };
    }
    return {
      status: "CORRECT",
      message: "Correct!  You guessed it.",
      attempts,
      finished: true,
      secretNumber: state.secret,
    };
  }

  async function submitGuess(guess) {
    if (state.mode === "local") {
      return localGuess(guess);
    }
    return postJson(GUESS_URL, { gameId: state.gameId, guess });
  }

  /* ------------------------------------------------------------
     Starting a round
     ------------------------------------------------------------ */

  async function startGame() {
    if (state.busy) {
      return;
    }

    setBusy(true);
    setFeedback("info", "i", "Picking a secret number…");

    try {
      const data = await loadNewGame();

      state.gameId = data.gameId;
      state.attempts = data.attempts || 0;
      state.min = Number.isInteger(data.min) ? data.min : 1;
      state.max = Number.isInteger(data.max) ? data.max : 100;
      state.low = state.min;
      state.high = state.max;
      state.finished = false;

      el.history.replaceChildren();
      el.input.value = "";
      el.input.maxLength = String(state.max).length;
      el.input.placeholder = "?";

      setFeedback(
        "info",
        "i",
        (state.mode === "local" ? "Browser-only version. " : "") +
          "I picked a number between " + state.min + " and " + state.max + ". Take a guess!"
      );
    } catch (error) {
      handleError(error);
    } finally {
      setBusy(false);
      renderStats();
      if (canPlay()) {
        el.input.focus();
      }
    }
  }

  /* ------------------------------------------------------------
     Sending a guess
     ------------------------------------------------------------ */

  async function handleSubmit(event) {
    event.preventDefault();

    if (!canPlay()) {
      return;
    }

    const raw = el.input.value.trim();
    if (raw === "") {
      rejectInput("Please type a number before guessing.");
      return;
    }

    const guess = Number(raw);
    if (!Number.isInteger(guess)) {
      rejectInput("Please type a whole number, for example 42.");
      return;
    }
    if (guess < state.min || guess > state.max) {
      rejectInput("Out of range. Enter a number between " + state.min + " and " + state.max + ".");
      return;
    }

    setBusy(true);

    try {
      const data = await submitGuess(guess);

      if (Number.isInteger(data.attempts)) {
        state.attempts = data.attempts;
      }

      if (data.status === "CORRECT") {
        state.finished = true;
        addChip(guess, "correct");
        renderStats();
        showWin(guess, state.attempts, data.secretNumber);
        el.input.value = "";
      } else if (data.status === "TOO_LOW" || data.status === "TOO_HIGH") {
        const isLow = data.status === "TOO_LOW";
        addChip(guess, isLow ? "low" : "high");

        if (isLow) {
          state.low = Math.max(state.low, guess + 1);
        } else {
          state.high = Math.min(state.high, guess - 1);
        }

        renderStats();
        setFeedback(
          isLow ? "low" : "high",
          isLow ? "↑" : "↓",
          data.message + " Attempts so far: " + state.attempts + "."
        );
        el.input.value = "";
      } else {
        setFeedback("error", "!", data.message || "That guess was not accepted.");
        shake();
      }
    } catch (error) {
      handleError(error);
    } finally {
      setBusy(false);
      renderStats();
      if (canPlay()) {
        el.input.focus();
      }
    }
  }

  function rejectInput(message) {
    setFeedback("error", "!", message);
    shake();
    el.input.select();
    el.input.focus();
  }

  function showWin(guess, attempts, secret) {
    const number = Number.isInteger(secret) ? secret : guess;
    const rating =
      attempts <= 5
        ? "Outstanding — you read my mind!"
        : attempts <= 8
          ? "Excellent guessing!"
          : attempts <= 12
            ? "Good work, nice use of the hints."
            : "You got there! Try to beat it next round.";

    setFeedback(
      "success",
      "✓",
      "Yes! The number was " + number + ". Solved in " + attempts +
        " attempt" + (attempts === 1 ? "" : "s") + ". " + rating
    );

    if (state.best === null || attempts < state.best) {
      state.best = attempts;
      writeBest(attempts);
    }

    confetti();
  }

  /* ------------------------------------------------------------
     Errors
     ------------------------------------------------------------ */

  function handleError(error) {
    if (error instanceof ApiError) {
      if (error.status === 404 || error.status === 409) {
        state.gameId = null;
        state.finished = true;
        setFeedback("error", "!", error.message + " Press “New game” to continue.");
        return;
      }
      setFeedback("error", "!", error.message);
      shake();
      return;
    }

    state.gameId = null;
    state.finished = true;
    setFeedback(
      "error",
      "!",
      "Could not reach the game. Please load a new game."
    );
  }

  /* ------------------------------------------------------------
     Rendering
     ------------------------------------------------------------ */

  function renderStats() {
    el.attempts.textContent = String(state.attempts);
    el.best.textContent = state.best === null ? "–" : String(state.best);

    if (state.finished) {
      el.range.textContent = "Solved";
    } else {
      el.range.textContent = state.low + " – " + state.high;
    }
  }

  function setFeedback(kind, icon, message) {
    el.feedback.className = "feedback " + kind;
    const iconEl = el.feedback.querySelector(".feedback-icon");
    const textEl = el.feedback.querySelector(".feedback-text");
    if (iconEl) {
      iconEl.textContent = icon;
    }
    if (textEl) {
      textEl.textContent = message;
    }
  }

  function shake() {
    el.feedback.classList.remove("shake");
    // Force a reflow so the animation can run again.
    void el.feedback.offsetWidth;
    el.feedback.classList.add("shake");
  }

  function addChip(value, kind) {
    const item = document.createElement("li");
    item.className = "chip " + kind;

    const mark = document.createElement("span");
    mark.className = "chip-mark";
    mark.textContent = kind === "correct" ? "✓" : kind === "low" ? "↑" : "↓";

    const label = document.createElement("span");
    label.textContent = String(value);

    item.append(mark, label);
    el.history.append(item);
  }

  function syncControls() {
    const playable = canPlay();
    el.input.disabled = !playable;
    el.guessBtn.disabled = !playable;
    el.guessBtn.textContent = state.finished ? "Solved" : "Guess";
    el.newGame.textContent = state.finished ? "Play again" : "New game";
    el.newGame.classList.toggle("highlight", state.finished);
  }

  function canPlay() {
    return Boolean(state.gameId) && !state.finished && !state.busy;
  }

  function setBusy(busy) {
    state.busy = busy;
    syncControls();
  }

  /* ------------------------------------------------------------
     Best score storage
     ------------------------------------------------------------ */

  function readBest() {
    try {
      const value = Number(localStorage.getItem(BEST_KEY));
      return Number.isInteger(value) && value > 0 ? value : null;
    } catch (error) {
      return null;
    }
  }

  function writeBest(value) {
    try {
      localStorage.setItem(BEST_KEY, String(value));
    } catch (error) {
      /* Storage can be blocked; the game still works. */
    }
  }

  /* ------------------------------------------------------------
     Small celebration
     ------------------------------------------------------------ */

  function confetti() {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      return;
    }

    const colors = ["#7c5cff", "#22d3ee", "#34d399", "#fbbf24", "#fb7185"];
    const layer = document.createElement("div");
    layer.className = "confetti-layer";

    for (let i = 0; i < 36; i++) {
      const piece = document.createElement("span");
      piece.className = "confetti";
      piece.style.left = Math.random() * 100 + "vw";
      piece.style.background = colors[i % colors.length];
      piece.style.animationDuration = 1.6 + Math.random() * 1.3 + "s";
      piece.style.animationDelay = Math.random() * 0.35 + "s";
      layer.append(piece);
    }

    document.body.append(layer);
    window.setTimeout(() => layer.remove(), 3400);
  }

  /* ------------------------------------------------------------
     Wiring
     ------------------------------------------------------------ */

  el.form.addEventListener("submit", handleSubmit);
  el.newGame.addEventListener("click", startGame);

  // Keep the number field numeric only.
  el.input.addEventListener("input", () => {
    const cleaned = el.input.value.replace(/[^\d]/g, "");
    if (cleaned !== el.input.value) {
      el.input.value = cleaned;
    }
  });

  el.howToBtn.addEventListener("click", () => {
    el.howTo.open = !el.howTo.open;
  });

  el.howTo.addEventListener("toggle", () => {
    el.howToBtn.setAttribute("aria-expanded", String(el.howTo.open));
    el.howToBtn.textContent = el.howTo.open ? "Hide rules" : "How to play";
  });

  syncControls();
  renderStats();
  startGame();
})();
