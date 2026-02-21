'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { GEO } from '@/lib/geo';
import type { User } from '@/types';
import { Plus, Trash2 } from 'lucide-react';

export default function UsersPage() {
  const queryClient = useQueryClient();
  const { data: users, isLoading } = useQuery({
    queryKey: ['users'],
    queryFn: () => api.get<User[]>('/api/v1/users'),
  });

  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({ username: '', password: '', displayName: '', role: 'MANAGER' });

  const createUser = useMutation({
    mutationFn: (data: typeof form) => api.post('/api/v1/users', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      setShowCreate(false);
      setForm({ username: '', password: '', displayName: '', role: 'MANAGER' });
      toast.success(GEO.create);
    },
    onError: (err: any) => toast.error(err.message),
  });

  const toggleActive = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      api.put(`/api/v1/users/${id}`, { active }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  });

  const deleteUser = useMutation({
    mutationFn: (id: string) => api.delete(`/api/v1/users/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      toast.success(GEO.delete);
    },
    onError: (err: any) => toast.error(err.message),
  });

  return (
    <div className="p-4 space-y-4">
      <div className="flex justify-end">
        <Button onClick={() => setShowCreate(true)}>
          <Plus className="h-4 w-4 mr-2" />
          {GEO.addUser}
        </Button>
      </div>

      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-16 w-full" />
          ))}
        </div>
      ) : (
        <div className="border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-muted">
              <tr>
                <th className="px-4 py-3 text-left">{GEO.username}</th>
                <th className="px-4 py-3 text-left">{GEO.displayName}</th>
                <th className="px-4 py-3 text-center">{GEO.role}</th>
                <th className="px-4 py-3 text-center">{GEO.active}</th>
                <th className="px-4 py-3 text-center"></th>
              </tr>
            </thead>
            <tbody>
              {users?.map((user) => (
                <tr key={user.userId} className="border-t">
                  <td className="px-4 py-3">{user.username}</td>
                  <td className="px-4 py-3">{user.displayName}</td>
                  <td className="px-4 py-3 text-center">
                    <Badge variant="secondary">{user.role}</Badge>
                  </td>
                  <td className="px-4 py-3 text-center">
                    <Button
                      variant={user.active ? 'outline' : 'destructive'}
                      size="sm"
                      onClick={() => toggleActive.mutate({ id: user.userId, active: !user.active })}
                    >
                      {user.active ? GEO.active : 'არააქტიური'}
                    </Button>
                  </td>
                  <td className="px-4 py-3 text-center">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => {
                        if (confirm(`${user.displayName} - ${GEO.delete}?`)) {
                          deleteUser.mutate(user.userId);
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
            <DialogTitle>{GEO.addUser}</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <Input
              placeholder={GEO.username}
              value={form.username}
              onChange={(e) => setForm({ ...form, username: e.target.value })}
            />
            <Input
              type="password"
              placeholder={GEO.password}
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
            />
            <Input
              placeholder={GEO.displayName}
              value={form.displayName}
              onChange={(e) => setForm({ ...form, displayName: e.target.value })}
            />
            <select
              value={form.role}
              onChange={(e) => setForm({ ...form, role: e.target.value })}
              className="w-full h-11 px-3 rounded-md border border-input bg-background text-sm"
            >
              <option value="MANAGER">MANAGER</option>
              <option value="ACCOUNTANT">ACCOUNTANT</option>
              <option value="ADMIN">ADMIN</option>
            </select>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowCreate(false)}>{GEO.cancel}</Button>
            <Button onClick={() => createUser.mutate(form)} disabled={createUser.isPending}>
              {GEO.create}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
