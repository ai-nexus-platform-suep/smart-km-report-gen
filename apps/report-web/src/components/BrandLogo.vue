<template>
  <svg class="brand-logo" viewBox="0 0 44 44" role="img" aria-label="电力智能报告系统标识" xmlns="http://www.w3.org/2000/svg">
    <defs>
      <linearGradient id="brand-logo-main" x1="8" y1="7" x2="36" y2="37" gradientUnits="userSpaceOnUse">
        <stop offset="0" stop-color="#7BD7FF" />
        <stop offset="0.52" stop-color="#00B8D9" />
        <stop offset="1" stop-color="#1E6BFF" />
      </linearGradient>
      <linearGradient id="brand-logo-bolt" x1="18" y1="15" x2="27" y2="34" gradientUnits="userSpaceOnUse">
        <stop offset="0" stop-color="#F8FBFF" />
        <stop offset="1" stop-color="#00B8D9" />
      </linearGradient>
    </defs>

    <rect class="logo-frame" x="1" y="1" width="42" height="42" />
    <path class="logo-paper" d="M14 9.5H26.5L32 15V34.5H14V9.5Z" />
    <path class="logo-fold" d="M26.5 10V15H31.5" />
    <path class="logo-line" d="M17.5 18H25.5" />
    <path class="logo-line" d="M17.5 23H22.5" />
    <path class="logo-bolt" d="M24.5 16L17.5 27H23L20.5 35L29 22.5H23.5L24.5 16Z" />
    <rect class="logo-node" x="8" y="12" width="4" height="4" />
    <rect class="logo-node" x="8" y="28" width="4" height="4" />
    <path class="logo-circuit" d="M12 14H14" />
    <path class="logo-circuit" d="M12 30H14" />
  </svg>
</template>

<style scoped>
.brand-logo {
  display: block;
}

.logo-frame {
  fill: rgba(255, 255, 255, 0.03);
  stroke: rgba(255, 255, 255, 0.2);
  stroke-width: 1;
}

.logo-paper {
  fill: rgba(0, 184, 217, 0.08);
  stroke: url("#brand-logo-main");
  stroke-linejoin: round;
  stroke-width: 1.6;
}

.logo-fold,
.logo-line,
.logo-circuit {
  fill: none;
  stroke: rgba(248, 251, 255, 0.74);
  stroke-linecap: square;
  stroke-linejoin: round;
  stroke-width: 1.5;
}

.logo-line {
  opacity: 0.72;
}

.logo-bolt {
  fill: url("#brand-logo-bolt");
}

.logo-node {
  fill: none;
  stroke: #00b8d9;
  stroke-width: 1.4;
}
</style>
