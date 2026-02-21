'use client';

import { X } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { GEO } from '@/lib/geo';
import type { SelectedCustomer } from '@/types';

interface SelectedPanelProps {
  items: SelectedCustomer[];
  onRemove: (index: number) => void;
  onCommentChange: (index: number, comment: string) => void;
}

export function SelectedPanel({ items, onRemove, onCommentChange }: SelectedPanelProps) {
  if (items.length === 0) return null;

  return (
    <div className="border-b bg-card">
      <div className="flex items-center justify-between px-4 py-2 border-b">
        <Badge variant="default">{GEO.selected}: {items.length}</Badge>
      </div>
      <div className="max-h-[40vh] overflow-y-auto">
        {items.map((item, index) => (
          <div key={index} className="flex items-center gap-2 px-4 py-2 border-b last:border-b-0">
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium truncate">{item.customerName}</p>
              <Input
                placeholder={GEO.comment}
                value={item.comment}
                onChange={(e) => onCommentChange(index, e.target.value)}
                className="mt-1 h-8 text-xs"
              />
            </div>
            <button
              onClick={() => onRemove(index)}
              className="p-2 min-w-[44px] min-h-[44px] flex items-center justify-center text-muted-foreground hover:text-destructive"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
