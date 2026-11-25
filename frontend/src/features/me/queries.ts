import { useQuery } from "@tanstack/react-query"
import { api } from "@/api/client.ts"
import { ApiError } from "@/api/errors.ts"

export const useMe = () =>
    useQuery({
        queryKey: ["me"],
        queryFn: async () => {
            const { data, error } = await api.GET("/api/me")
            if (error) throw new ApiError(error)

            return data
        },
    })
