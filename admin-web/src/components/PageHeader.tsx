import { LucideIcon } from 'lucide-react';
import { ReactNode } from 'react';

export function PageHeader({
  icon: Icon,
  title,
  description,
  actions,
}: {
  icon: LucideIcon;
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="page-header">
      <div className="page-title">
        <span className="page-icon">
          <Icon aria-hidden />
        </span>
        <div>
          <h1>{title}</h1>
          {description && <p>{description}</p>}
        </div>
      </div>
      {actions && <div className="filters">{actions}</div>}
    </div>
  );
}
