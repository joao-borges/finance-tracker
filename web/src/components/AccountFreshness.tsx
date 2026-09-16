import { Tooltip } from "antd";
import type { Account } from "../lib/api";
import styles from "./AccountFreshness.module.css";

/** Past this many days behind, a connection is treated as broken rather than quiet. */
const STALE_DAYS = 3;

const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * How far an account's data actually reaches, which is not the same as when we
 * last asked: a dead bank connection keeps answering at the bridge with a stale
 * balance date. Showing the gap is what makes a broken connection visible
 * without anyone having to read a log.
 */
export default function AccountFreshness({ account }: { account: Account }) {
    if (!account.syncedThrough) {
        return <span className={styles.unknown}>—</span>;
    }
    const through = new Date(account.syncedThrough);
    const daysBehind = Math.floor((Date.now() - through.getTime()) / DAY_MS);
    const stale = daysBehind >= STALE_DAYS;
    const label = daysBehind <= 0 ? "today" : `${daysBehind}d behind`;

    return (
        <Tooltip title={`Data known good through ${through.toLocaleString()}`}>
            <span className={stale ? styles.stale : styles.fresh}>
                {stale ? "⚠ " : ""}
                {label}
            </span>
        </Tooltip>
    );
}
