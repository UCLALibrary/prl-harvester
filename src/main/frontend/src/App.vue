<script setup>
import { version } from '../package.json';
import HarvesterAdmin from './components/HarvesterAdmin.vue'
</script>

<template>
  <header>
    <h1>PRL Harvester Admin</h1>
  </header>

  <main>
    <HarvesterAdmin />
  </main>

  <footer>PRL Harvester Admin v{{ version }}</footer>
</template>

<style scoped>
header {
  padding-bottom: 2rem;
}

footer {
  font-style: italic;
  padding-top: 2rem;
  text-align: right;
}
</style>
