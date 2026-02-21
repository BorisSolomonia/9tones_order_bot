'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import type { Draft, SelectedCustomer } from '@/types';

export function useDrafts() {
  return useQuery({
    queryKey: ['drafts'],
    queryFn: () => api.get<Draft[]>('/api/v1/drafts'),
  });
}

export function useDraftSuggestion() {
  return useQuery({
    queryKey: ['draft-suggest'],
    queryFn: async (): Promise<Draft | null> => {
      try {
        const result = await api.get<Draft | null>('/api/v1/drafts/suggest');
        return result ?? null;
      } catch {
        return null;
      }
    },
  });
}

export function useCreateDraft() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: { name: string; items: SelectedCustomer[] }) =>
      api.post<Draft>('/api/v1/drafts', {
        name: data.name,
        items: data.items.map((i) => ({
          customerName: i.customerName,
          customerId: i.customerId,
          comment: i.comment,
        })),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['drafts'] });
    },
  });
}

export function useUpdateDraft() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: { id: string; name: string; items: SelectedCustomer[] }) =>
      api.put<Draft>(`/api/v1/drafts/${data.id}`, {
        name: data.name,
        items: data.items.map((i) => ({
          customerName: i.customerName,
          customerId: i.customerId,
          comment: i.comment,
        })),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['drafts'] });
    },
  });
}

export function useDeleteDraft() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.delete(`/api/v1/drafts/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['drafts'] });
    },
  });
}

export function useLoadDraft() {
  return useMutation({
    mutationFn: (id: string) => api.post<Draft>(`/api/v1/drafts/${id}/load`),
  });
}
