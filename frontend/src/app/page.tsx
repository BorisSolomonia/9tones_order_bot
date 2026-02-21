'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/providers/auth-provider';
import { Skeleton } from '@/components/ui/skeleton';

export default function HomePage() {
  const { user, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (loading) return;
    if (!user) {
      router.replace('/login');
      return;
    }
    switch (user.role) {
      case 'MANAGER':
        router.replace('/orders');
        break;
      case 'ACCOUNTANT':
        router.replace('/reports');
        break;
      case 'ADMIN':
        router.replace('/users');
        break;
      default:
        router.replace('/login');
    }
  }, [user, loading, router]);

  return (
    <div className="flex items-center justify-center min-h-screen">
      <Skeleton className="h-8 w-48" />
    </div>
  );
}
