// Decoding of a block extension (the node's extension.fields: [[keyHex, valueHex], …]).
// The first key byte says what a field is:
//   0x00 system parameter — only in the first block of each 1024-block voting epoch; value is a 4-byte integer
//   0x01 NiPoPoW interlinks — value = 1 byte "how many consecutive levels" + the 32-byte id of the block they point to
//   0x02 validation settings — serialized rule updates, shown as hex

// parameter ids as defined by the Ergo protocol (org.ergoplatform.settings.Parameters)
const PARAMETERS = {
  1: ['Storage fee factor', 'nanoERG per byte per storage period'],
  2: ['Min value per byte', 'nanoERG'],
  3: ['Max block size', 'bytes'],
  4: ['Max block cost', 'cost units'],
  5: ['Token access cost', 'cost units'],
  6: ['Input cost', 'cost units'],
  7: ['Data input cost', 'cost units'],
  8: ['Output cost', 'cost units'],
  9: ['Sub-blocks per block', ''],
  120: ['Soft-fork', ''],
  121: ['Soft-fork votes collected', ''],
  122: ['Soft-fork starting height', ''],
  123: ['Block version', ''],
  124: ['Soft-fork disabling rules', 'serialized'],
}

const int32 = (hex) => {
  const v = parseInt(hex, 16)
  return v > 0x7fffffff ? v - 0x100000000 : v
}

export function decodeExtension(fields) {
  const parameters = []
  const interlinks = []
  const other = []
  for (const [key, value] of fields) {
    const prefix = parseInt(key.slice(0, 2), 16)
    const id = parseInt(key.slice(2), 16)
    if (prefix === 0x00) {
      const [name, unit] = PARAMETERS[id] || [`Parameter ${id}`, '']
      // most parameters are 4-byte integers; anything else (e.g. serialized rules) stays as hex
      parameters.push({ key, id, name, unit, value: value.length === 8 ? int32(value) : null, raw: value })
    } else if (prefix === 0x01 && value.length === 66) {
      const count = parseInt(value.slice(0, 2), 16)
      interlinks.push({ key, fromLevel: id, toLevel: id + count - 1, count, blockId: value.slice(2) })
    } else {
      other.push({ key, name: prefix === 0x02 ? 'Validation settings' : 'Unknown field', raw: value })
    }
  }
  parameters.sort((a, b) => a.id - b.id)
  interlinks.sort((a, b) => a.fromLevel - b.fromLevel)
  return { parameters, interlinks, other }
}
