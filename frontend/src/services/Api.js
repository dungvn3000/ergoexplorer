import axios from 'axios'

// Dev: the Jooby backend listens on port 8080. Prod: same host, the reverse proxy forwards /api to the backend.
let baseURL = 'http://localhost:8080/api/v1'
if (process.env.NODE_ENV === 'production') {
  baseURL = '/api/v1'
}

const api = axios.create({ baseURL: baseURL })

const useBaseURL = () => {
  return baseURL
}

export { api, useBaseURL }
