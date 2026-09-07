import {
  HttpClient,
  HttpErrorResponse,
  HttpEventType,
  HttpParams,
  HttpResponse,
} from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, filter, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, DocumentSummary, DocumentType, UpdateDocumentRequest } from './models';
import { unwrap } from './unwrap';

/** What `upload` needs. `file` is handed straight to `FormData` and never read here — see `upload`'s
 * own comment for why. */
export interface UploadDocumentRequest {
  readonly studentId: string;
  readonly documentType: DocumentType;
  /** ISO `yyyy-mm-dd`, or omitted — a photo or a birth certificate carries neither date. */
  readonly issueDate?: string;
  readonly expiryDate?: string;
  readonly file: File;
}

/**
 * What `upload` reports as the request goes: progress while bytes are still travelling, then the
 * finished document once the server has answered. A discriminated union rather than one object
 * with optional fields, so a subscriber's `switch` on `kind` is what the compiler checks rather
 * than a pair of fields nothing stops both or neither being set on.
 */
export type UploadEvent =
  | { readonly kind: 'progress'; readonly percent: number }
  | { readonly kind: 'done'; readonly document: DocumentSummary };

/**
 * A student's certificates, photo, signature and other documents (ADR-0025).
 *
 * **Confidential (ADR-0014).** A document's type and original filename can be enough on their own
 * to identify a child, so nothing here may log a value, and no method may put one in a path.
 *
 * **There is no method that returns a document's bytes.** `contentUrl` gives the address a plain
 * `<a>` downloads from — see its own comment for why a normal browser navigation, not
 * `HttpClient`, is how this app fetches a document's content.
 */
@Injectable({ providedIn: 'root' })
export class DocumentsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/documents`;

  /** Every document a student has, newest first. Bounded per student, so this is never paged. */
  list(studentId: string): Observable<readonly DocumentSummary[]> {
    return this.http
      .get<ApiResponse<readonly DocumentSummary[]>>(this.baseUrl, {
        params: new HttpParams().set('studentId', studentId),
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /**
   * `GET /api/documents/{id}/content`, proxied and audited on every request (ADR-0025: no signed
   * URL, ever). A relative, same-origin path — `environment.apiBaseUrl` is always `/api` — so a
   * plain `<a [href]>` navigation carries the session cookie exactly as any other request does,
   * with none of the complexity of fetching a blob through `HttpClient` only to hand it back to
   * the browser as one. The server's own `Content-Disposition: attachment` is what turns that
   * navigation into a download rather than the browser trying to render the file in place of this
   * app; a failure (a 403, a 503) opens as a bare JSON page in the new tab this app opens for
   * exactly that reason, rather than replacing the record the user was looking at.
   */
  contentUrl(id: string): string {
    return `${this.baseUrl}/${id}/content`;
  }

  /**
   * Uploads a new document, reporting progress as it goes.
   *
   * The `File` is handed straight to `FormData` and never read here — the same reason
   * `StudentImport`'s CSV upload never reads its file either: its bytes are a scanned certificate
   * or a child's photo (Confidential, ADR-0014), and this app has no reason to have seen them.
   *
   * `413` — the container refusing an oversized upload before it reaches a controller — arrives as
   * an `HttpErrorResponse` the same as any other failure; see `apiErrorStatus` and this method's
   * caller for how that is told apart from a refusal the backend put a code on.
   */
  upload(request: UploadDocumentRequest): Observable<UploadEvent> {
    const body = new FormData();
    body.append('file', request.file);

    let params = new HttpParams()
      .set('studentId', request.studentId)
      .set('documentType', request.documentType);
    if (request.issueDate) {
      params = params.set('issueDate', request.issueDate);
    }
    if (request.expiryDate) {
      params = params.set('expiryDate', request.expiryDate);
    }

    return this.http
      .post<ApiResponse<DocumentSummary>>(this.baseUrl, body, {
        params,
        withCredentials: true,
        reportProgress: true,
        observe: 'events',
      })
      .pipe(
        filter(
          (event) =>
            event.type === HttpEventType.UploadProgress || event.type === HttpEventType.Response,
        ),
        map((event): UploadEvent => {
          if (event.type === HttpEventType.UploadProgress) {
            const percent = event.total ? Math.round((100 * event.loaded) / event.total) : 0;
            return { kind: 'progress', percent };
          }
          // The only other event this stream lets through is the final Response (filtered above).
          const response = (event as HttpResponse<ApiResponse<DocumentSummary>>).body;
          if (!response?.success || response.data === undefined) {
            // Mirrors `unwrap`'s own check — this method cannot pipe through it because it also
            // has to let progress events pass, which `unwrap`'s `map` does not expect.
            throw new HttpErrorResponse({ error: response ?? undefined });
          }
          return { kind: 'done', document: response.data };
        }),
      );
  }

  /** Corrects a document's type, dates or verification status. Never the file itself. */
  update(id: string, request: UpdateDocumentRequest): Observable<DocumentSummary> {
    return this.http
      .put<ApiResponse<DocumentSummary>>(`${this.baseUrl}/${id}`, request, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /** A 204 carries no envelope at all, so nothing here may unwrap one. */
  delete(id: string): Observable<void> {
    return this.http
      .delete<ApiResponse<unknown> | null>(`${this.baseUrl}/${id}`, { withCredentials: true })
      .pipe(discardBody);
  }
}

const discardBody = map((): void => undefined);
