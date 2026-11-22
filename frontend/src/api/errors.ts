import type { components } from "@/api/schema"

export type ProblemDetail = components["schemas"]["ProblemDetail"]

// openapi-fetch returns an error body instead of throwing. A query only fails when
// its function throws, so the query functions wrap the body in this class.
export class ApiError extends Error {
    readonly problem: ProblemDetail

    constructor(problem: ProblemDetail) {
        super(problem.title)
        this.name = "ApiError"
        this.problem = problem
    }
}
