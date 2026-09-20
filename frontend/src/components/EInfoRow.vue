<template>
  <div class="row e-info-row">
    <div class="col-12 col-sm-4 col-md-3 text-grey-7">{{ label }}</div>
    <div class="col-12 col-sm-8 col-md-9 value"><slot /></div>
  </div>
</template>
<script setup>
defineProps({
  label: String,
})

defineOptions({
  name: 'EInfoRow',
})
</script>
<style scoped lang="scss">
.e-info-row {
  padding: 8px 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  &:last-child {
    border-bottom: 0;
  }
  .value {
    word-break: break-all;
  }
}
</style>
