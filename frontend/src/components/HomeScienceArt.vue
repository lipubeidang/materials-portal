<template>
  <svg
    class="home-science-art"
    viewBox="0 0 740 500"
    fill="none"
    aria-hidden="true"
  >
    <defs>
      <linearGradient
        id="science-frame"
        x1="170"
        y1="60"
        x2="580"
        y2="380"
        gradientUnits="userSpaceOnUse"
      >
        <stop stop-color="#7de9ff" />
        <stop offset=".45" stop-color="#dffcff" />
        <stop offset="1" stop-color="#177fee" />
      </linearGradient>
      <linearGradient
        id="science-screen"
        x1="180"
        y1="70"
        x2="560"
        y2="380"
        gradientUnits="userSpaceOnUse"
      >
        <stop stop-color="#174889" stop-opacity=".95" />
        <stop offset="1" stop-color="#0873b1" stop-opacity=".4" />
      </linearGradient>
      <radialGradient id="science-orb" cx=".3" cy=".2" r=".8">
        <stop stop-color="white" />
        <stop offset=".25" stop-color="#aef5ff" />
        <stop offset=".6" stop-color="#3fb3f5" />
        <stop offset="1" stop-color="#2253aa" />
      </radialGradient>
      <radialGradient id="science-halo">
        <stop stop-color="#2489fc" stop-opacity=".5" />
        <stop offset="1" stop-color="#2489fc" stop-opacity="0" />
      </radialGradient>
      <filter id="science-glow" x="-50%" y="-50%" width="200%" height="200%">
        <feGaussianBlur stdDeviation="5" />
      </filter>
    </defs>
    <ellipse cx="380" cy="425" rx="320" ry="72" fill="url(#science-halo)" />
    <g stroke="#4ac9ff">
      <ellipse cx="380" cy="425" rx="255" ry="48" opacity=".3" />
      <ellipse cx="380" cy="425" rx="215" ry="36" opacity=".55" />
      <ellipse
        cx="380"
        cy="425"
        rx="175"
        ry="25"
        stroke-width="3"
        opacity=".65"
      />
      <path d="M100 420h-45m590 0h45M380 470v20M380 375v-16" opacity=".5" />
    </g>
    <path
      d="M170 84 569 61 583 365 170 379Z"
      stroke="#4ad5ff"
      stroke-width="12"
      opacity=".4"
      filter="url(#science-glow)"
    />
    <path
      d="M170 84 569 61 583 365 170 379Z"
      fill="url(#science-screen)"
      stroke="url(#science-frame)"
      stroke-width="5"
    />
    <path d="M172 112 571 91" stroke="#8ae3ff" opacity=".65" />
    <g fill="#a2e8ff">
      <circle cx="188" cy="98" r="3" />
      <circle cx="201" cy="97" r="3" />
      <circle cx="214" cy="96" r="3" />
    </g>
    <g stroke="#59b9f0" opacity=".18">
      <path
        d="M200 160 545 144M200 205 546 188M200 250 548 232M200 295 550 277M245 129v217m70-221v218m70-223v220m70-226v224m70-229v225"
      />
    </g>
    <g stroke="#adefff" stroke-width="7" stroke-linecap="round">
      <path
        d="m276 195 84-48 87 38 43 75-91 64-95-34-28-95 123 129m-39-177 39 177m-95-34 143-105m-171 10 214 65"
      />
    </g>
    <g fill="url(#science-orb)">
      <circle cx="276" cy="195" r="25" />
      <circle cx="360" cy="147" r="22" />
      <circle cx="447" cy="185" r="30" />
      <circle cx="490" cy="260" r="23" />
      <circle cx="399" cy="324" r="30" />
      <circle cx="304" cy="290" r="21" />
    </g>
    <g stroke="#64c9ff" opacity=".5">
      <path
        d="M105 130h35v70h20M595 190h45v-55h35M585 310h65v45h30M195 45V25h80"
      />
      <circle cx="101" cy="130" r="4" />
      <circle cx="679" cy="135" r="4" />
      <circle cx="684" cy="355" r="4" />
    </g>
    <g fill="#87d7ff" opacity=".6">
      <circle cx="102" cy="270" r="3" />
      <circle cx="614" cy="68" r="3" />
      <circle cx="655" cy="250" r="2" />
      <circle cx="130" cy="350" r="2" />
      <circle cx="465" cy="30" r="3" />
    </g>
  </svg>
</template>
