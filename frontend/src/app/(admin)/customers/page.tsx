'use client';

import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { GEO } from '@/lib/geo';
import { FRONTEND_CONFIG } from '@/lib/config';
import type { Customer } from '@/types';
import { Plus, Search, Trash2 } from 'lucide-react';

export default function CustomersPage() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [debounced, setDebounced] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({ name: '', tin: '' });

  useEffect(() => {
    const t = setTimeout(() => setDebounced(search), FRONTEND_CONFIG.search.debounceMs);
    return () => clearTimeout(t);
  }, [search]);

  const { data: customers, isLoading } = useQuery({
    queryKey: ['admin-customers', debounced],
    queryFn: () =>
      api.get<Customer[]>(
        `/api/v1/customers?search=${encodeURIComponent(debounced)}&size=${FRONTEND_CONFIG.customers.adminPageSize}`
      ),
  });

  const createCustomer = useMutation({
    mutationFn: (data: typeof form) => api.post('/api/v1/customers', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-customers'] });
      setShowCreate(false);
      setForm({ name: '', tin: '' });
      toast.success(GEO.create);
    },
    onError: (err: any) => toast.error(err.message),
  });

  const toggleActive = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      api.put(`/api/v1/customers/${id}`, { active }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-customers'] }),
  });

  const deleteCustomer = useMutation({
    mutationFn: (id: string) => api.delete(`/api/v1/customers/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-customers'] });
      toast.success(GEO.delete);
    },
    onError: (err: any) => toast.error(err.message),
  });

  return (
    <div className="p-4 space-y-4">
      <div className="flex gap-2">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder={GEO.search}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
          />
        </div>
        <Button onClick={() => setShowCreate(true)}>
          <Plus className="h-4 w-4 mr-2" />
          {GEO.addCustomer}
        </Button>
      </div>

      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-14 w-full" />
          ))}
        </div>
      ) : (
        <div className="border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-muted">
              <tr>
                <th className="px-4 py-3 text-left">{GEO.customerName}</th>
                <th className="px-4 py-3 text-left">{GEO.tin}</th>
                <th className="px-4 py-3 text-center">Score</th>
                <th className="px-4 py-3 text-center">{GEO.active}</th>
                <th className="px-4 py-3 text-center"></th>
              </tr>
            </thead>
            <tbody>
              {customers?.map((c) => (
                <tr key={c.customerId} className="border-t">
                  <td className="px-4 py-3">{c.name}</td>
                  <td className="px-4 py-3 text-muted-foreground">{c.tin}</td>
                  <td className="px-4 py-3 text-center">{c.frequencyScore}</td>
                  <td className="px-4 py-3 text-center">
                    <Button
                      variant={c.active ? 'outline' : 'destructive'}
                      size="sm"
                      onClick={() => toggleActive.mutate({ id: c.customerId, active: !c.active })}
                    >
                      {c.active ? GEO.active : GEO.inactive}
                    </Button>
                  </td>
                  <td className="px-4 py-3 text-center">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => {
                        if (confirm(`${c.name} - ${GEO.delete}?`)) {
                          deleteCustomer.mutate(c.customerId);
                        }
                      }}
                    >
                      <Trash2 className="h-4 w-4 text-destructive" />
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Dialog open={showCreate} onOpenChange={setShowCreate}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{GEO.addCustomer}</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <Input
              placeholder={GEO.customerName}
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
            />
            <Input
              placeholder={GEO.tin}
              value={form.tin}
              onChange={(e) => setForm({ ...form, tin: e.target.value })}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowCreate(false)}>{GEO.cancel}</Button>
            <Button onClick={() => createCustomer.mutate(form)} disabled={createCustomer.isPending}>
              {GEO.create}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

