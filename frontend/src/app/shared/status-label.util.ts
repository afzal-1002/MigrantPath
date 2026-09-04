/**
 * Post-MVP UX Milestone UX1 - a single place to turn a backend enum value into
 * user-friendly wording (brief §43/§44: "avoid raw enum display" / "user-friendly
 * language"). Previously duplicated ad hoc in case-list/case-detail
 * (`status.replaceAll('_', ' ').toLowerCase()...`) - centralized here so the
 * dashboard's own status chips use the exact same wording as the case pages, not a
 * second, subtly different mapping.
 */
export function formatStatusLabel(status: string): string {
  return status
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/^./, (c) => c.toUpperCase());
}
