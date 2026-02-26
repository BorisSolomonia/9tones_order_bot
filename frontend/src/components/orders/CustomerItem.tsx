'use client';

import { Checkbox } from '@/components/ui/checkbox';
import { Star } from 'lucide-react';
import { cn } from '@/lib/utils';
import { GEO } from '@/lib/geo';
import type { Customer } from '@/types';

interface CustomerItemProps {
  customer: Customer;
  isSelected: boolean;
  isMy: boolean;
  comment: string;
  onToggle: () => void;
  onToggleMy: () => void;
  onCommentChange: (comment: string) => void;
}

export function CustomerItem({ customer, isSelected, isMy, comment, onToggle, onToggleMy, onCommentChange }: CustomerItemProps) {
  return (
    <div
      className={cn(
        'flex items-center gap-2 px-3 py-1.5 border-b transition-colors',
        isSelected && 'bg-primary/5'
      )}
    >
      <Checkbox checked={isSelected} onCheckedChange={onToggle} className="h-4 w-4 shrink-0" />
      <button
        onClick={onToggle}
        className="min-w-0 shrink-0 max-w-[35%] text-left min-h-[36px] flex flex-col justify-center"
      >
        <span className="text-[11px] leading-tight truncate">{customer.name}</span>
        {customer.board && (
          <span className="text-[9px] text-primary/70 truncate">{customer.board}</span>
        )}
      </button>
      <input
        type="text"
        placeholder={GEO.comment}
        value={comment}
        onChange={(e) => onCommentChange(e.target.value)}
        onClick={(e) => e.stopPropagation()}
        className="flex-1 min-w-0 h-7 px-1.5 text-[10px] focus:text-xs rounded border border-input bg-background placeholder:text-muted-foreground/50 focus:outline-none focus:ring-1 focus:ring-ring transition-all"
      />
      <button
        onClick={(e) => {
          e.stopPropagation();
          onToggleMy();
        }}
        className="p-1 shrink-0"
      >
        <Star
          className={cn(
            'h-3.5 w-3.5 transition-colors',
            isMy ? 'fill-yellow-400 text-yellow-400' : 'text-muted-foreground'
          )}
        />
      </button>
    </div>
  );
}
