import { setupServer } from "msw/node"

// No default handlers: each test states the answers it needs.
export const server = setupServer()
