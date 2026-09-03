# Admin Role Permissions

Authoritative source: `SecurityConfig`'s URL-pattern matchers under `/api/v1/admin/**` and
`/api/v1/internal/content/**`. This table documents what those matchers actually enforce -
if this table and the code ever disagree, the code wins; update this table.

| Action | USER | CONTENT_EDITOR | LEGAL_REVIEWER | ADMIN |
|---|---|---|---|---|
| View admin pages/lists/detail (any content type) | No | Yes | Yes | Yes |
| Create a Procedure/Rule/Threshold identity | No | Yes | No | Yes |
| Create/edit a DRAFT version | No | Yes | No | Yes |
| Add/edit/remove draft steps, documents, fees | No | Yes | No | Yes |
| Attach a source to a draft version | No | Yes | No | Yes |
| Create an Official Source | No | Yes | No | Yes |
| Submit a version for review | No | Yes | No | Yes |
| Run validate / dry-run | No | Yes | Yes | Yes |
| Approve a version under review | No | No | Yes\* | Yes\* |
| Request changes (send back to DRAFT) | No | No | Yes\* | Yes\* |
| Verify / mark a source outdated | No | No | Yes | Yes |
| Publish an APPROVED version | No | No | No | Yes |
| Archive a PUBLISHED version | No | No | No | Yes |
| Copy a published version into a new draft | No | Yes | No | Yes |
| View the review queue | No | Yes | Yes | Yes |
| View the audit log | No | No | No | Yes |
| Search accounts / view roles | No | No | No | Yes |
| Assign / remove an administrative role | No | No | No | Yes |
| View private user Assessments/Cases | No | No | No | **No - never** |

\* Subject to the separation-of-duties rule below: the account that submitted a version can
never approve/reject its own submission, even if it holds LEGAL_REVIEWER (see
[CONTENT_REVIEW_WORKFLOW.md](CONTENT_REVIEW_WORKFLOW.md)).

## Notes

- Every row above is enforced twice: by a specific `SecurityConfig` URL matcher (authoritative)
  and, for the approve/self-approval case specifically, again by `ContentReviewCoordinator` at
  the service layer (defense in depth - a role check alone cannot express "not the same person
  who submitted this").
- No role - not even ADMIN - is ever granted access to another user's `Assessment`,
  `Recommendation`, or `UserCase` through any admin endpoint. Admin impact-analysis endpoints
  return counts only, never identities.
- `ADMIN` is the only role that can assign or remove roles, and cannot remove their own last
  `ADMIN` role (`CANNOT_REMOVE_OWN_LAST_ADMIN_ROLE`) - this prevents a single mistaken action
  from locking every administrator out.
