# RB1 dashcam API and safe shared-EC2 deployment

This is an additive `RB1DashcamStack` owned by this repository. It does not manage or import the EC2 instance, its AMI, Elastic IP, security groups, existing applications, or their CloudFormation stacks as mutable resources. It targets the existing host through an SSM association and attaches one newly owned, distinctly named inline policy to the discovered instance role.

The shared host serves **Piggy Ledger, Midlife Motors, Kaedama Cycles, and The Cranky Goose**. Do not use direct `cdk deploy`, hotswap, unchecked change-set execution, a production stack-policy override, or existing application deployment scripts to install RB1. Never replace or restart the host to fix an application deployment. All four applications must remain healthy.

## Resources

- Private SSE-S3 encrypted footage bucket, with Block Public Access, bucket-owner-enforced ownership, HTTPS-only policy, `routine/` expiry after 30 days, no expiry for `saved/`, and abandoned multipart cleanup after seven days. Lifecycle age is measured from upload, not original capture date.
- Separate retained metadata/auth DynamoDB table with TTL and point-in-time recovery. Expiration is checked by the API; TTL deletion is asynchronous.
- Retained encrypted S3 access/audit log bucket with finite retention, scoped CloudTrail object data events (management events disabled), and a CloudWatch storage-budget alarm at 100 GiB. The alarm is visible in CloudWatch; no email/SNS subscription is provisioned.
- A new `rb1-dashcam-host-access` inline policy. The existing host role and all unrelated policies remain untouched. This grants only the RB1 table, video prefixes, and the exact packaged application asset.
- An SSM association named `RB1DashcamStack-api-deployment` targeting exactly the discovered host ID.

These resources add metadata, audit, alarm, and request costs beyond the approximately $1.25/month video-storage estimate. CDK is development tooling only. The API's production dependency audit is clean as of implementation; the pinned CDK tooling bundles minimatch with a reported moderate glob-amplification advisory. No untrusted glob input is accepted by this deployment workflow. Recheck advisories and update the lockfile when CDK publishes a fixed bundle.

## Build and validate without AWS changes

Node.js 24+, npm, AWS CLI v2, and the existing CDK bootstrap are prerequisites. No secrets need to be copied out of Piggy Ledger.

```sh
npm ci --ignore-scripts
npm test
npm run synth -- -c instanceId=i-00000000000000000 -c instanceRoleName=ValidationOnlyRole -c domain=dashcam.example.com --quiet
./build.sh
```

The placeholder synth is for inspection only. Never deploy using a copied instance ID or role name. The guarded path discovers them from the live `PiggyLedgerStack` in account `294281109519`, region `us-east-1`.

## Prepare a concrete deployment

1. Choose a new API hostname. The default is `dashcam.midlifemotor.club`. Existing apex and www site names are forbidden. Configure its DNS to the existing shared Elastic IP, discovering the current value from Piggy Ledger's live stack. This workflow deliberately creates no DNS or network resources. The hostname must resolve correctly before execution so Caddy can obtain its certificate and the HTTPS health check can pass.
2. Confirm AWS credentials for the intended account and install the dependencies above. Do not bootstrap, modify the host, or change any existing application as an incidental step.
3. Prepare only:

```sh
npm run deploy -- --profile YOUR_PROFILE --domain dashcam.midlifemotor.club
```

The script checks account, existing stack policies/templates, running host ID/AMI/IP/profile, existing inline and attached role policies, successful SSM associations for **all four** applications, apex sites, www redirects, Piggy Ledger API health, and Kaedama's quote endpoint. A missing dependency or failed health check blocks preparation.

A successful empty `GetStackPolicy` response is recorded as no policy for the three dependent application stacks. Piggy Ledger's exact shared-host protection policy remains mandatory. AWS permission/credential failures and malformed responses still block deployment and include the underlying error; they are never treated as an absent policy.

It runs tests, synthesizes, diffs, and prepares a CloudFormation change set using `--no-execute`. It rejects resource types outside the RB1 allowlist, a broad/changed SSM target, any removal, and any replacement, including conditional replacement. The CloudFormation template retains the data resources. Review the exact template, changes, host snapshot, IAM permissions, and SSM script recorded in ignored `infra/review.json`. Its CloudFormation change-set ARN, not a newly synthesized template, is used for execution.

Preparation can publish the RB1 asset and create a CloudFormation review change set. It does **not** execute the SSM installation or change the running applications.

## Execute only after review

```sh
npm run deploy -- --profile YOUR_PROFILE --domain dashcam.midlifemotor.club --execute
```

The review expires after one hour. Execution rechecks the live snapshot and four sites and compares both the exact change list and original CloudFormation template with the review. It refuses if anything changed. It then executes only that change-set ARN and waits for CloudFormation/SSM success before checking the shared host and all dependent stacks/policies/sites again. No original stack policy is changed or disabled.

If the CLI exits after submitting a change set, do not submit it again. `ExecuteChangeSet` normally has an empty successful response. Finish the read-only checks against the retained review with:

```sh
npm run deploy -- --profile YOUR_PROFILE --domain dashcam.midlifemotor.club --verify
```

Verification performs no deployment or execution. It requires a completed RB1 stack, the exact deployed template, the unchanged shared-host snapshot, successful SSM installation on that host, and healthy HTTPS endpoints for RB1 and the four existing sites. It is also available after the one-hour execution review expires. Future executions use a deterministic CloudFormation request token and reject an already-submitted change set.

The host script:

- Requires Node 24 already installed, a healthy Caddy service, the shared Caddy import contract, and a free/RB1-owned loopback port 3081. It installs no host packages and modifies no firewall or networking.
- Takes `/run/lock/shared-caddy-deploy.lock` and checks all four existing sites before doing RB1 work.
- Stages a versioned release under `/opt/rb1/releases`, installs only that release's locked production dependencies, and runs under a new unprivileged `rb1-api` account.
- Changes only the RB1 symlink, `/etc/rb1/api.env`, `/etc/systemd/system/rb1-dashcam.service`, and `/etc/caddy/conf.d/rb1-dashcam.caddy`. It never rewrites `/etc/caddy/Caddyfile` or another site's snippet.
- Sets a 192 MiB memory limit, 25% CPU quota, filesystem hardening, and a loopback-only API listener. It restarts only `rb1-dashcam`, validates Caddy before a reload, and hashes pre-existing Caddy files and service units to verify they are unchanged.
- Restores the prior RB1 route/unit/environment/symlink on an installation/health failure. It never stops another application or restarts Caddy/EC2. A first-install failure can leave an inactive RB1 Unix account or release directory; these do not route traffic or change other applications.

The host role is still shared: Unix users and systemd hardening do not create a strong IAM isolation boundary. A compromised same-host process may use the instance role's permissions. Separate compute is required if stronger isolation becomes necessary.

## Pair the phone

After a successful deployment, open an authorized Session Manager shell on the shared host. Generate a one-time code using the RB1 environment and application only:

```sh
sudo sh -c 'set -a; . /etc/rb1/api.env; set +a; node /opt/rb1/current/admin.cjs pair'
```

Enter the API HTTPS address and the generated code in the app's **Camera** screen while connected to unmetered Wi-Fi. The code expires after ten minutes and is accepted only once. Do not post the code into source control, issue trackers, or shared logs. A lost enrollment response requires a fresh code; subsequent refresh/upload retries are recoverable automatically.

There are no passwords or AWS keys to configure in Android. See [Authentication mechanism](../docs/DASHCAM_DESIGN.md#authentication-mechanism-implemented) for renewal, storage, expiry, reboot behavior, and revocation.

## Owner operations

The phone displays its non-secret device ID. Use the same authorized shell/environment as above to run:

```sh
node /opt/rb1/current/admin.cjs list DEVICE_ID
node /opt/rb1/current/admin.cjs download DEVICE_ID CLIP_ID
node /opt/rb1/current/admin.cjs revoke DEVICE_ID
```

These commands require the RB1 environment variables, so prefix them with the `sudo sh -c 'set -a; ...'` pattern when necessary. `download` prints a five-minute bearer URL for that verified clip. Treat it as private. `revoke` blocks renewal and all new API access immediately; already-issued S3 URLs can remain usable until expiry. Delete protected cloud copies explicitly through an authorized S3 owner operation if they are no longer wanted. Local deletion or allowing local overwrite intentionally does not delete protected cloud footage.

## Validation still required before relying on this

The initial owner-run deployment has completed and passed read-only verification of the deployed template, shared-host snapshot, SSM installation, API health, and all four existing sites. Device/real-S3 integration tests remain pending. For subsequent deployments, first verify the exact prepared change set and DNS. After deployment, test enrollment, a real interrupted multipart upload, remote checksum verification and playback, revocation, and retry after phone reboot. While parked, verify camera framing and orientation, final partial clips, an hour of continuous recording, fragment/clip boundaries, free-space handling, incident protection, thermal shutdown, and uploads after ignition-off with screen off. Check all original speedometer/audio/brightness functions. Avoid making a recording-reliability claim until the actual phone passes these tests.
