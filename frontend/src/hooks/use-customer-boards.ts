'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import type { CustomerLocation } from '@/types';

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
      queryClient.invalidateQueries({ queryKey: ['customer-locations', customerId] });
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
      queryClient.invalidateQueries({ queryKey: ['customer-locations', customerId] });
      queryClient.invalidateQueries({ queryKey: ['admin-customers'] });
    },
  });
}

export function useCustomerLocations(customerId: string) {
  return useQuery({
    queryKey: ['customer-locations', customerId],
    queryFn: () => api.get<CustomerLocation[]>(`/api/v1/customers/${customerId}/locations`),
    enabled: !!customerId,
  });
}

export function useAddLocation(customerId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (location: { board: string; address?: string | null }) =>
      api.post<void>(`/api/v1/customers/${customerId}/locations`, location),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customer-locations', customerId] });
      queryClient.invalidateQueries({ queryKey: ['customer-boards', customerId] });
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      queryClient.invalidateQueries({ queryKey: ['admin-customers'] });
    },
  });
}

export function useRemoveLocation(customerId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ board, address }: { board: string; address?: string | null }) => {
      const params = new URLSearchParams({ board });
      if (address) params.set('address', address);
      return api.delete<void>(`/api/v1/customers/${customerId}/locations?${params.toString()}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customer-locations', customerId] });
      queryClient.invalidateQueries({ queryKey: ['customer-boards', customerId] });
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      queryClient.invalidateQueries({ queryKey: ['admin-customers'] });
    },
  });
}
