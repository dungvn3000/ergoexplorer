<template>
  <div class="col-md-3 col-sm-12 col-xs-12">
    <q-item :style="`background-color: ${color}`" class="q-pa-none rounded-borders">
      <q-item-section class="q-pa-md q-ml-none text-white">
        <q-item-label class="text-white text-h6 text-weight-bolder">
          {{ text }}
        </q-item-label>
        <q-item-label>{{ title }}</q-item-label>
      </q-item-section>
      <q-item-section side class="q-mr-md text-white">
        <q-icon :name="icon" color="white" size="44px" />
      </q-item-section>
    </q-item>
  </div>
</template>
<script setup>
defineProps({
  color: String,
  title: String,
  text: String,
  icon: String,
})

defineOptions({
  name: 'StaticWidget',
})
</script>
