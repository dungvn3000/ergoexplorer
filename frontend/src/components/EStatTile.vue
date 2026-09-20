<template>
  <q-card flat bordered class="stat-card">
    <q-card-section class="row items-center no-wrap">
      <div class="stat-icon" :style="{ background: bg, color: color }">
        <q-icon :name="icon" size="26px" />
      </div>
      <div class="q-ml-sm" style="min-width: 0">
        <div class="text-caption text-grey-6">{{ label }}</div>
        <div class="text-h6 text-weight-bolder no-wrap num">
          {{ value }}<span v-if="unit" class="text-caption text-grey-6 q-ml-xs">{{ unit }}</span>
        </div>
        <div class="text-caption text-grey-6 ellipsis" v-if="sub">{{ sub }}</div>
      </div>
    </q-card-section>
  </q-card>
</template>

<script setup>
defineProps({
  label: String,
  value: [String, Number],
  unit: String,
  sub: String,
  icon: String,
  color: String,
  bg: String,
})

defineOptions({
  name: 'EStatTile',
})
</script>

<style scoped lang="scss">
.stat-card {
  height: 100%;
  transition: transform 0.2s ease;
  &:hover {
    transform: translateY(-2px);
  }
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}

.num {
  font-variant-numeric: tabular-nums;
  line-height: 1.3;
}
</style>
