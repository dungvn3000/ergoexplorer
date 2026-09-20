import TLoading from 'components/TLoading.vue'
import TInput from 'components/TInput.vue'
import EHash from 'components/EHash.vue'
import EAddress from 'components/EAddress.vue'
import EErg from 'components/EErg.vue'
import EToken from 'components/EToken.vue'
import EInfoRow from 'components/EInfoRow.vue'
import EBox from 'components/EBox.vue'
import ERawJson from 'components/ERawJson.vue'

export default ({ app }) => {
  app.component('t-loading', TLoading)
  app.component('t-input', TInput)
  // Explorer
  app.component('e-hash', EHash)
  app.component('e-address', EAddress)
  app.component('e-erg', EErg)
  app.component('e-token', EToken)
  app.component('e-info-row', EInfoRow)
  app.component('e-box', EBox)
  app.component('e-raw-json', ERawJson)
}
