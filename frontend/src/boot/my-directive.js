export default ({ app }) => {
  app.directive('highlight', {
    mounted: (el) => {
      el.classList.add('is-highlight')
    },
  })
}
