import { defineConfig } from "deepsec/config";

export default defineConfig({
  ai: { mode: "local", provider: "local" },
  projects: [
    {
      id: "numeris_android",
      root: "..",
    },
  ],
});
