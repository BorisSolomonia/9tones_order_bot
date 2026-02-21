'use client';

import { Button } from '@/components/ui/button';
import { Download } from 'lucide-react';
import { GEO } from '@/lib/geo';
import { buildApiUrl } from '@/lib/api';

interface ExportButtonProps {
  dateFrom?: string;
  dateTo?: string;
  managerId?: string;
}

export function ExportButton({ dateFrom, dateTo, managerId }: ExportButtonProps) {
  const handleExport = () => {
    const params = new URLSearchParams();
    if (dateFrom) params.set('date_from', dateFrom);
    if (dateTo) params.set('date_to', dateTo);
    if (managerId) params.set('manager_id', managerId);
    params.set('format', 'csv');
    window.open(buildApiUrl(`/api/v1/orders/export?${params.toString()}`), '_blank');
  };

  return (
    <Button variant="outline" onClick={handleExport}>
      <Download className="h-4 w-4 mr-2" />
      {GEO.export}
    </Button>
  );
}
