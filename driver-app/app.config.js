// Extends app.json with build-time settings.
// GOOGLE_SERVICES_JSON: path to the Firebase Android config (google-services.json). Without it the app
// builds and runs normally, but cannot receive push notifications and relies on polling.
module.exports = ({ config }) => ({
  ...config,
  android: {
    ...config.android,
    ...(process.env.GOOGLE_SERVICES_JSON ? { googleServicesFile: process.env.GOOGLE_SERVICES_JSON } : {}),
  },
});
