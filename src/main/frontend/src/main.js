import { createApp } from "vue"
import { createVuetify } from "vuetify"
import * as components from "vuetify/components"
import * as directives from "vuetify/directives"
import { aliases, mdi } from "vuetify/iconsets/mdi"

import App from "./App.vue"

import "vuetify/styles"
import "@mdi/font/css/materialdesignicons.css"
import "./assets/main.css"

const vuetify = createVuetify({
    components,
    directives,
    icons: { aliases, sets: { mdi } },
})

createApp(App).use(vuetify).mount("#app")
