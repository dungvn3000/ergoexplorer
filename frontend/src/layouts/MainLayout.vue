<template>
  <q-layout view="lHr LpR lff">
    <q-header bordered class="bg-white">
      <ExplorerTopBar :leftDrawerOpen="leftDrawerOpen" @toggleLeftDrawer="toggleLeftDrawer" />
    </q-header>

    <q-drawer v-model="leftDrawerOpen" bordered dark :width="253">
      <ExplorerSideBar :key="'sideBar' + $route.fullPath" />
    </q-drawer>

    <q-page-container>
      <q-page class="q-pa-md">
        <router-view />
      </q-page>
    </q-page-container>
  </q-layout>
</template>
<script setup>
import { useStorage } from '@vueuse/core'
import ExplorerTopBar from './TopBar.vue'
import ExplorerSideBar from './SideBar.vue'

const leftDrawerOpen = useStorage('leftDrawerOpen', true)

function toggleLeftDrawer() {
  leftDrawerOpen.value = !leftDrawerOpen.value
}

defineOptions({
  name: 'MainLayout',
})
</script>
