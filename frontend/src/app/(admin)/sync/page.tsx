'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { GEO } from '@/lib/geo';
import { formatDateTime } from '@/lib/utils';
import type { SyncState } from '@/types';
import { RefreshCw, Download } from 'lucide-react';

export default function SyncPage() {
  const queryClient = useQueryClient();

  const { data: status } = useQuery({
    queryKey: ['sync-status'],
    queryFn: async () => {
      try {
        return await api.get<SyncState>('/api/v1/sync/status');
      } catch {
        return null;
      }
    },
    refetchInterval: 10000,
  });

  const { data: history, isLoading } = useQuery({
    queryKey: ['sync-history'],
    queryFn: () => api.get<SyncState[]>('/api/v1/sync/history?limit=10'),
  });

  const triggerSync = useMutation({
    mutationFn: (type: string) => api.post('/api/v1/sync/trigger', { type }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sync-status'] });
      queryClient.invalidateQueries({ queryKey: ['sync-history'] });
      toast.success(GEO.triggerSync);
    },
    onError: (err: any) => toast.error(err.message),
  });

  const isRunning = status?.status === 'RUNNING';

  const statusVariant = (s: string) => {
    switch (s) {
      case 'SUCCESS': return 'success' as const;
      case 'FAILED': return 'destructive' as const;
      case 'RUNNING': return 'default' as const;
      default: return 'secondary' as const;
    }
  };

  return (
    <div className="p-4 space-y-6">
      {/* Full Sync — prominent */}
      <div className="border-2 border-primary/30 rounded-lg p-4 space-y-3 bg-primary/5">
        <div className="flex items-start gap-3">
          <Download className="h-5 w-5 text-primary mt-0.5 shrink-0" />
          <div className="flex-1">
            <h3 className="font-medium text-sm">{GEO.fullSync}</h3>
            <p className="text-xs text-muted-foreground mt-1">{GEO.fullSyncDesc}</p>
          </div>
        </div>
        <Button
          onClick={() => triggerSync.mutate('FULL')}
          disabled={triggerSync.isPending || isRunning}
          className="w-full"
          size="lg"
        >
          <Download className="h-4 w-4 mr-2" />
          {isRunning ? GEO.running : GEO.fullSync}
        </Button>
      </div>

      {/* Current status */}
      {status && (
        <div className="border rounded-lg p-4 space-y-2">
          <h3 className="font-medium text-sm">{GEO.syncStatus}</h3>
          <div className="flex items-center gap-2">
            <Badge variant={statusVariant(status.status)}>
              {status.status === 'RUNNING' ? GEO.running : status.status === 'SUCCESS' ? GEO.success : GEO.failed}
            </Badge>
            <span className="text-xs text-muted-foreground">{status.syncType}</span>
          </div>
          {status.status === 'SUCCESS' && (
            <p className="text-xs">
              {GEO.customersFound}: {status.customersFound}, {GEO.customersAdded}: {status.customersAdded}
            </p>
          )}
          {status.errorMessage && (
            <p className="text-xs text-destructive">{status.errorMessage}</p>
          )}
        </div>
      )}

      {/* Daily sync */}
      <div className="flex gap-2">
        <Button
          variant="outline"
          onClick={() => triggerSync.mutate('DAILY')}
          disabled={triggerSync.isPending || isRunning}
        >
          <RefreshCw className="h-4 w-4 mr-2" />
          {GEO.dailySync}
        </Button>
      </div>

      {/* History */}
      <div>
        <h3 className="font-medium text-sm mb-3">{GEO.syncHistory}</h3>
        {isLoading ? (
          <div className="space-y-2">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-14 w-full" />
            ))}
          </div>
        ) : (
          <div className="border rounded-lg overflow-hidden">
            <table className="w-full text-xs">
              <thead className="bg-muted">
                <tr>
                  <th className="px-3 py-2 text-left">Type</th>
                  <th className="px-3 py-2 text-left">{GEO.date}</th>
                  <th className="px-3 py-2 text-center">{GEO.status}</th>
                  <th className="px-3 py-2 text-center">{GEO.customersAdded}</th>
                </tr>
              </thead>
              <tbody>
                {history?.filter((s) => s.syncId).map((s, i) => (
                  <tr key={s.syncId || i} className="border-t">
                    <td className="px-3 py-2">{s.syncType}</td>
                    <td className="px-3 py-2">{formatDateTime(s.completedAt || s.startedAt)}</td>
                    <td className="px-3 py-2 text-center">
                      <Badge variant={statusVariant(s.status)}>{s.status}</Badge>
                    </td>
                    <td className="px-3 py-2 text-center">{s.customersAdded}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
