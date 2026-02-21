'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { ClipboardList, Clock, FileText } from 'lucide-react';
import { cn } from '@/lib/utils';
import { GEO } from '@/lib/geo';

const navItems = [
  { href: '/orders', label: GEO.orders, icon: ClipboardList },
  { href: '/history', label: GEO.history, icon: Clock },
  { href: '/drafts', label: GEO.drafts, icon: FileText },
];

export function BottomNav() {
  const pathname = usePathname();

  return (
    <nav className="fixed bottom-0 left-0 right-0 z-40 border-t bg-background pb-safe">
      <div className="flex items-center justify-around">
        {navItems.map((item) => {
          const isActive = pathname === item.href;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                'flex flex-1 flex-col items-center gap-1 py-3 text-xs transition-colors min-h-[44px] justify-center',
                isActive ? 'text-primary font-medium' : 'text-muted-foreground'
              )}
            >
              <item.icon className="h-5 w-5" />
              <span>{item.label}</span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
