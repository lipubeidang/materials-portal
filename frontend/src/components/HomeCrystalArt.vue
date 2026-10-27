<template>
  <svg
    class="home-science-art"
    viewBox="0 0 740 500"
    fill="none"
    aria-hidden="true"
  >
    <defs>
      <linearGradient
        id="crystal-face"
        x1="220"
        y1="90"
        x2="520"
        y2="380"
        gradientUnits="userSpaceOnUse"
      >
        <stop stop-color="#74efff" stop-opacity=".75" />
        <stop offset="1" stop-color="#2474e4" stop-opacity=".15" />
      </linearGradient>
      <radialGradient id="crystal-point">
        <stop stop-color="white" />
        <stop offset=".3" stop-color="#b0f6ff" />
        <stop offset="1" stop-color="#167bdd" />
      </radialGradient>
      <radialGradient id="crystal-floor">
        <stop stop-color="#1fa3ec" stop-opacity=".5" />
        <stop offset="1" stop-color="#237cfc" stop-opacity="0" />
      </radialGradient>
    </defs>
    <ellipse cx="380" cy="425" rx="310" ry="65" fill="url(#crystal-floor)" />
    <g stroke="#4ccfff" opacity=".5">
      <ellipse cx="380" cy="423" rx="244" ry="38" />
      <ellipse cx="380" cy="423" rx="192" ry="24" stroke-width="3" />
      <path d="M88 202h66v-46h47M552 282h70v72h50M480 72h95v41M106 394h60" />
    </g>
    <g stroke="#9eeeff" stroke-width="2.5" stroke-linejoin="round">
      <path
        d="m380 70 157 88v176l-157 88-157-88V158Z"
        fill="url(#crystal-face)"
      />
      <path
        d="m223 158 157 89 157-89M380 247v175M380 70v177m-157 87 157-87 157 87"
      />
      <path
        d="m301 114 157 88v176M459 114 302 202v176m-79-132 157 88 157-88m-314 0 157-88 157 88m-157-88v176"
        opacity=".55"
      />
    </g>
    <g fill="url(#crystal-point)">
      <circle cx="380" cy="70" r="20" />
      <circle cx="223" cy="158" r="24" />
      <circle cx="537" cy="158" r="24" />
      <circle cx="380" cy="247" r="33" />
      <circle cx="223" cy="334" r="24" />
      <circle cx="537" cy="334" r="24" />
      <circle cx="380" cy="422" r="20" />
      <circle cx="301" cy="114" r="10" />
      <circle cx="459" cy="114" r="10" />
      <circle cx="302" cy="202" r="12" />
      <circle cx="458" cy="202" r="12" />
      <circle cx="302" cy="378" r="10" />
      <circle cx="458" cy="378" r="10" />
    </g>
    <g fill="#8ceaff" opacity=".7">
      <circle cx="160" cy="95" r="3" />
      <circle cx="609" cy="169" r="4" />
      <circle cx="646" cy="282" r="3" />
      <circle cx="132" cy="308" r="3" />
    </g>
  </svg>
</template>
