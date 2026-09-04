import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Account } from './account';

describe('Account', () => {
  let fixture: ComponentFixture<Account>;
  let component: Account;
  let httpMock: HttpTestingController;
  let dialogOpenSpy: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    const dialogRefStub = { afterClosed: () => of(false) } as unknown as MatDialogRef<unknown>;
    await TestBed.configureTestingModule({
      imports: [Account],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Account);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    // Spying on the real, injected MatDialog's own open() method (rather than providing a
    // stand-in object via useValue) - MatDialogModule's own providers, contributed through the
    // component's own `imports`, otherwise win over a TestBed-level useValue override for this
    // service.
    dialogOpenSpy = vi
      .spyOn(TestBed.inject(MatDialog), 'open')
      .mockReturnValue(dialogRefStub) as unknown as ReturnType<typeof vi.fn>;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('exports data by calling the export endpoint and triggering a download', () => {
    const createObjectURLSpy = vi
      .spyOn(URL, 'createObjectURL')
      .mockReturnValue('blob:mock');
    const revokeSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);

    component.export();
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/account/export`);
    expect(req.request.method).toBe('GET');
    req.flush(new Blob(['{}'], { type: 'application/json' }));

    expect(component['exporting']()).toBe(false);
    expect(createObjectURLSpy).toHaveBeenCalled();
    expect(revokeSpy).toHaveBeenCalled();
  });

  it('shows an error if export fails', () => {
    component.export();
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/account/export`);
    req.flush(new Blob(['error']), { status: 500, statusText: 'Server Error' });

    expect(component['exportError']()).toContain('Could not generate');
  });

  it('opens the delete-account confirmation dialog', () => {
    component.openDeleteDialog();
    expect(dialogOpenSpy).toHaveBeenCalled();
  });

  it('clears local session state and navigates home once the dialog confirms deletion', () => {
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');
    const confirmedRef = { afterClosed: () => of(true) } as unknown as MatDialogRef<unknown>;
    dialogOpenSpy.mockReturnValue(confirmedRef);

    component.openDeleteDialog();

    expect(navigateSpy).toHaveBeenCalledWith('/');
  });
});
