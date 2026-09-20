<template>
  <div class="fullscreen text-center q-pa-md flex flex-center">
    <div>
      <img src="/img/logo.svg" alt="Ergo Explorer" width="200px" />
      <div style="font-size: 5vh">404</div>
      <div class="text-h4" style="opacity: 0.4">Hi there, if you are lost please go home...</div>
      <q-btn class="q-mt-xl" color="green" icon="home" unelevated to="/" label="Go home" no-caps />
    </div>
  </div>
</template>

<script setup>
defineOptions({
  name: 'ErrorNotFound',
})
</script>
