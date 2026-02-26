'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';

export function useCustomerBoards(customerId: string) {
  return useQuery({
    queryKey: ['customer-boards', customerId],
    queryFn: () => api.get<string[]>(`/api/v1/customers/${customerId}/boards`),
    enabled: !!customerId,
  });
}

export function useAddBoard(customerId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (board: string) =>
      api.post<void>(`/api/v1/customers/${customerId}/boards`, { board }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customer-boards', customerId] });
      queryClient.invalidateQueries({ queryKey: ['admin-customers'] });
    },
  });
}

export function useRemoveBoard(customerId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (board: string) =>
      api.delete<void>(`/api/v1/customers/${customerId}/boards/${encodeURIComponent(board)}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customer-boards', customerId] });
      queryClient.invalidateQueries({ queryKey: ['admin-customers'] });
    },
  });
}
