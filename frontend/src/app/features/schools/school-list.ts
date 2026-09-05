import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { SchoolApi } from '../../core/api/school-api';
import { School } from '../../core/api/models';

/**
 * Schools listing. Deliberately unstyled beyond layout — presentation components get designed by
 * hand and moved into shared/components; this screen only shows the data-loading pattern.
 */
@Component({
  selector: 'cb-school-list',
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './school-list.html',
  styleUrl: './school-list.scss',
})
export class SchoolList {
  private readonly schoolApi = inject(SchoolApi);

  protected readonly loadFailed = signal(false);

  protected readonly schools = toSignal(
    this.schoolApi.list().pipe(
      catchError(() => {
        this.loadFailed.set(true);
        return of([] as School[]);
      }),
    ),
    { initialValue: undefined },
  );

  protected readonly isLoading = computed(() => this.schools() === undefined);
}
