<template>
  <router-view />
</template>

<script setup>
const version = import.meta.env.VITE_APP_VERSION
console.log(`App Version: ${version}`)
</script>
