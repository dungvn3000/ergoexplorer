<template>
  <div class="fullscreen bg-blue text-white text-center q-pa-md flex flex-center">
    <div>
      <div style="font-size: 10vh">500</div>
      <div class="text-h2" style="opacity: 0.4">Oops. Something went wrong...</div>
      <q-btn class="q-mt-xl" color="white" icon="home" text-color="blue" unelevated to="/" label="Go Home" no-caps />
    </div>
  </div>
</template>

<script setup>
defineOptions({
  name: 'ErrorPage',
})
</script>
