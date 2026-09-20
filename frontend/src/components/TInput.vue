<template>
  <slot :error="hasError" :error-message="errorMsg"> </slot>
</template>
<script setup>
import { computed } from 'vue'

const hasError = computed(() => {
  if (error) {
    return error[name] != null
  }
  return false
})

const errorMsg = computed(() => {
  if (error && error[name]) {
    return error[name]
  }
  return ''
})

const { error, name } = defineProps({
  error: Object,
  name: String,
})

defineOptions({
  name: 'TInput',
})
</script>
