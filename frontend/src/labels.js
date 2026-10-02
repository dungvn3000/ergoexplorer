// Display names for addresses and tokens (plain functions, kept apart from the component modules).
import { short } from './format.js'

// system addresses the backend does not label (it labels block.miner and address.label itself)
const LABELS = {
  '2iHkR7CWvD1R4j1yZg5bkeDRQavjAaVPeTDFGGLZduHyfWMuYpmhHocX8GJoaieTx78FntzJbCBVL6rf96ocJoZdmWBL2fci7NqWgAirppPQmZ7fN9V6z13Ay6brPriBKYqLp1bT2Fk4FkFLCfdPpe': 'Mining fee contract',
  '2Z4YBkDsDvQj8BX7xiySFewjitqp2ge9c99jfes2whbtKitZTxdBYqbrVZUvZvKv6aqn9by4kp3LE1c26LCyosFnVnm6b6U1JYvWpYmL2ZnixJbXLjWAWuBThV1D6dLpqZJYQHYDznJCk49g5TUiS4q8khpag2aNmHwREV7JSsypHdHLgJT7MGaw51aJfNubyzSKxZ4AJXFS27EfXwyCLzW1K6GVqwkJtCoPvrcLqmqwacAWJPkmh78nke9H4oT88XmSbRt2n9aWZjosiZCafZ4osUDxmZcc5QVEeTWn8drSraY3eFKe8Mu9MSCcVU':
    'Emission contract',
  '6KxusedL87PBibr1t1f4ggzAyTAmWEPqSpqXbkdoybNwHVw5Nb7cUESBmQw5XK8TyvbQiueyqkR9XMNaUgpWx3jT54p': 'Pay-to-reemission (EIP-27)',
  '22WkKcVUvboYCZJe1urbmvBL3j67LKb5KEAvFhJXqA6ubYvHpSCvbvwvEY3xzUr7QvxpEtqjzMAPMsVdZh1VGWmZphvKoJdVzL1ayhsMftTtEFoA3YYdq3zKeeYXavVrrPUmK3fRXJ2HWEbZexewtBWcgAnHBw5tKvYFy9dEUi645gE2fYMUvVBtbvMExE9mjZ2W9goWkqu1VtThAsMZWZWjHxDjX116HpeQKu9b9neEUBj4kE5sX8QXaV6ZeReXxYHFJFg2rmaTknSPMxHXA8NpQKgzryBwLssp5EJ1QTqn5R6xuvGgFCEUZicCEo8qk8UNbE7e2d4WqW5qzpQPzJkKoPa5UtJEPYDWNhaCKmCpzdSc77':
    'Re-emission contract (EIP-27)',
}

export const addressLabel = (address, backendLabel) => backendLabel || LABELS[address] || null

// system contracts whose ERG is not in circulation: the emission box (not issued yet) and the EIP-27 re-emission
// contracts (issued but locked); the "circulating" supply already leaves it out, so shares of it must too
const NON_CIRCULATING = new Set(Object.keys(LABELS).filter((a) => /^(Emission|Pay-to-reemission|Re-emission)/.test(LABELS[a])))
export const isNonCirculating = (address) => NON_CIRCULATING.has(address)

export const tokenName = (t) => t.name || short(t.tokenId || t.id, 6, 4)
