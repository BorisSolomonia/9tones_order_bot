'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { AuthGuard } from '@/components/layout/AuthGuard';
import { AppShell } from '@/components/layout/AppShell';
import { cn } from '@/lib/utils';
import { GEO } from '@/lib/geo';
import { Users, Building2, RefreshCw } from 'lucide-react';

const adminNav = [
  { href: '/users', label: GEO.users, icon: Users },
  { href: '/customers', label: GEO.customers, icon: Building2 },
  { href: '/sync', label: GEO.sync, icon: RefreshCw },
];

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();

  return (
    <AuthGuard allowedRoles={['ADMIN']}>
      <AppShell title="ადმინი">
        <div className="border-b">
          <nav className="flex">
            {adminNav.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  'flex items-center gap-2 px-4 py-3 text-sm font-medium min-h-[44px] transition-colors border-b-2',
                  pathname === item.href
                    ? 'text-primary border-primary'
                    : 'text-muted-foreground border-transparent'
                )}
              >
                <item.icon className="h-4 w-4" />
                {item.label}
              </Link>
            ))}
          </nav>
        </div>
        {children}
      </AppShell>
    </AuthGuard>
  );
}
