'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import type { MyCustomer } from '@/types';

export function useMyCustomersList() {
  return useQuery({
    queryKey: ['my-customers'],
    queryFn: () => api.get<MyCustomer[]>('/api/v1/customers/my'),
  });
}

export function useAddMyCustomer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: { customerName: string; customerId: string }) =>
      api.post('/api/v1/customers/my', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-customers'] });
      queryClient.invalidateQueries({ queryKey: ['customers'] });
    },
  });
}

export function useRemoveMyCustomer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (customerId: string) => api.delete(`/api/v1/customers/my/${customerId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-customers'] });
      queryClient.invalidateQueries({ queryKey: ['customers'] });
    },
  });
}
