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
import { useCustomerBoards, useAddBoard, useRemoveBoard } from '@/hooks/use-customer-boards';
import type { Customer } from '@/types';
import { Plus, Search, Trash2, X } from 'lucide-react';

export default function CustomersPage() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [debounced, setDebounced] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({ name: '', tin: '' });
  const [boardsCustomer, setBoardsCustomer] = useState<Customer | null>(null);

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

  // Deduplicate customers by customerId for the admin table (search returns one row per board)
  const uniqueCustomers = customers
    ? Array.from(new Map(customers.map((c) => [c.customerId, c])).values())
    : [];

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
                <th className="px-4 py-3 text-center">{GEO.boards}</th>
                <th className="px-4 py-3 text-center">{GEO.active}</th>
                <th className="px-4 py-3 text-center"></th>
              </tr>
            </thead>
            <tbody>
              {uniqueCustomers.map((c) => (
                <CustomerRow
                  key={c.customerId}
                  customer={c}
                  onToggleActive={() => toggleActive.mutate({ id: c.customerId, active: !c.active })}
                  onDelete={() => {
                    if (confirm(`${c.name} - ${GEO.delete}?`)) {
                      deleteCustomer.mutate(c.customerId);
                    }
                  }}
                  onManageBoards={() => setBoardsCustomer(c)}
                />
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

      {boardsCustomer && (
        <BoardsDialog
          customer={boardsCustomer}
          onClose={() => setBoardsCustomer(null)}
        />
      )}
    </div>
  );
}

function CustomerRow({
  customer,
  onToggleActive,
  onDelete,
  onManageBoards,
}: {
  customer: Customer;
  onToggleActive: () => void;
  onDelete: () => void;
  onManageBoards: () => void;
}) {
  const { data: boards } = useCustomerBoards(customer.customerId);

  return (
    <tr className="border-t">
      <td className="px-4 py-3">{customer.name}</td>
      <td className="px-4 py-3 text-muted-foreground">{customer.tin}</td>
      <td className="px-4 py-3 text-center">{customer.frequencyScore}</td>
      <td className="px-4 py-3 text-center">
        <button
          onClick={onManageBoards}
          className="inline-flex items-center gap-1 text-xs text-primary hover:underline"
        >
          <Badge variant="secondary" className="text-[10px]">
            {boards?.length ?? 0}
          </Badge>
          <span>{GEO.boards}</span>
        </button>
      </td>
      <td className="px-4 py-3 text-center">
        <Button
          variant={customer.active ? 'outline' : 'destructive'}
          size="sm"
          onClick={onToggleActive}
        >
          {customer.active ? GEO.active : GEO.inactive}
        </Button>
      </td>
      <td className="px-4 py-3 text-center">
        <Button
          variant="ghost"
          size="sm"
          onClick={onDelete}
        >
          <Trash2 className="h-4 w-4 text-destructive" />
        </Button>
      </td>
    </tr>
  );
}

function BoardsDialog({ customer, onClose }: { customer: Customer; onClose: () => void }) {
  const [newBoard, setNewBoard] = useState('');
  const { data: boards, isLoading } = useCustomerBoards(customer.customerId);
  const addBoard = useAddBoard(customer.customerId);
  const removeBoard = useRemoveBoard(customer.customerId);

  const handleAdd = async () => {
    const trimmed = newBoard.trim();
    if (!trimmed) return;
    try {
      await addBoard.mutateAsync(trimmed);
      setNewBoard('');
    } catch (err: any) {
      toast.error(err.message);
    }
  };

  const handleRemove = async (board: string) => {
    try {
      await removeBoard.mutateAsync(board);
    } catch (err: any) {
      toast.error(err.message);
    }
  };

  return (
    <Dialog open onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{GEO.boards} — {customer.name}</DialogTitle>
        </DialogHeader>

        <div className="space-y-3">
          {isLoading ? (
            <Skeleton className="h-8 w-full" />
          ) : boards && boards.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {boards.map((board) => (
                <Badge key={board} variant="secondary" className="text-xs flex items-center gap-1 pr-1">
                  {board}
                  <button
                    onClick={() => handleRemove(board)}
                    className="ml-1 hover:text-destructive transition-colors"
                  >
                    <X className="h-3 w-3" />
                  </button>
                </Badge>
              ))}
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">{GEO.noBoard}</p>
          )}

          <div className="flex gap-2">
            <Input
              placeholder={GEO.addBoard}
              value={newBoard}
              onChange={(e) => setNewBoard(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleAdd()}
            />
            <Button onClick={handleAdd} disabled={!newBoard.trim() || addBoard.isPending}>
              <Plus className="h-4 w-4" />
            </Button>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{GEO.cancel}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
