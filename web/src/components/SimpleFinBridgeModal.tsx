import { Alert, Button, Modal, Typography } from "antd";
import styles from "./SimpleFinBridgeModal.module.css";

interface Props {
    bridgeUrl: string | null;
    onClose: () => void;
}

// Where an unconnected install goes to create an account and mint a setup
// token. Once connected, the origin of the stored access URL wins, so a
// self-hosted bridge is honoured.
const PUBLIC_BRIDGE = "https://beta-bridge.simplefin.org/";

/**
 * The SimpleFIN bridge's own site, embedded so re-authenticating a bank
 * connection doesn't need a second tab. The bridge is a third-party origin:
 * browsers that block third-party cookies (Safari always, Chrome when set to)
 * won't carry the bridge session into the frame, so the fallback link out is
 * always offered rather than only after a failure.
 */
export default function SimpleFinBridgeModal({ bridgeUrl, onClose }: Props) {
    const target = bridgeUrl ?? PUBLIC_BRIDGE;

    return (
        <Modal
            open
            title="SimpleFIN bridge"
            onCancel={onClose}
            width="90vw"
            className={styles.modal}
            footer={
                <Button href={target} target="_blank" rel="noreferrer">
                    Open in a new tab
                </Button>
            }
        >
            <Alert
                type="info"
                showIcon
                className={styles.notice}
                message={
                    <Typography.Text>
                        Signed out here even though you're signed in to SimpleFIN? Your browser is blocking the
                        bridge's cookies inside a frame — open it in a new tab instead.
                    </Typography.Text>
                }
            />
            <iframe src={target} title="SimpleFIN bridge" className={styles.frame} />
        </Modal>
    );
}
