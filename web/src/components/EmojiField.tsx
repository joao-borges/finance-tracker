import { useState } from "react";
import { Button, Popover, Space } from "antd";
import EmojiPicker, { EmojiStyle, Theme, type EmojiClickData } from "emoji-picker-react";
import { useAppTheme } from "../theme/AppTheme";

interface Props {
    value?: string;
    onChange?: (value: string) => void;
}

/**
 * Emoji picker field — a button showing the chosen emoji that opens a searchable
 * picker. Stores the native unicode character. Works as a controlled input, so
 * it drops into antd Form items and plain state alike.
 */
export default function EmojiField({ value, onChange }: Props) {
    const { effective } = useAppTheme();
    const [open, setOpen] = useState(false);

    return (
        <Space>
            <Popover
                open={open}
                onOpenChange={setOpen}
                trigger="click"
                content={
                    <EmojiPicker
                        emojiStyle={EmojiStyle.NATIVE}
                        theme={effective === "dark" ? Theme.DARK : Theme.LIGHT}
                        onEmojiClick={(data: EmojiClickData) => {
                            onChange?.(data.emoji);
                            setOpen(false);
                        }}
                    />
                }
            >
                <Button>{value ? <span style={{ fontSize: 18 }}>{value}</span> : "Pick emoji"}</Button>
            </Popover>
            {value && (
                <Button type="text" onClick={() => onChange?.("")}>
                    Clear
                </Button>
            )}
        </Space>
    );
}
