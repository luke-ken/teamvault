import { useQuery } from "@tanstack/react-query"
import { api } from "@/api/client.ts"
import { ApiError } from "@/api/errors.ts"

export const usePing = () =>
    useQuery({
        queryKey: ["ping"],
        queryFn: async () => {
            const { data, error } = await api.GET("/api/ping")
            if (error) throw new ApiError(error)

            return data
        },
    })
