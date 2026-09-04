import { GlobalErrorHandler } from './global-error-handler';

describe(GlobalErrorHandler.name, () => {
  it('logs the error and does not throw (brief §138 - a broken handler must never itself crash the app)', () => {
    const handler = new GlobalErrorHandler();
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const error = new Error('synthetic test error');

    expect(() => handler.handleError(error)).not.toThrow();
    expect(consoleSpy).toHaveBeenCalledWith('Unhandled application error', error);

    consoleSpy.mockRestore();
  });

  it('never logs anything beyond the error object itself (no user/request data to leak)', () => {
    const handler = new GlobalErrorHandler();
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    handler.handleError(new Error('another synthetic error'));

    expect(consoleSpy).toHaveBeenCalledTimes(1);
    expect(consoleSpy.mock.calls[0]).toHaveLength(2);

    consoleSpy.mockRestore();
  });
});
