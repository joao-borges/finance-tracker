// Arithmetic for the floating calculators. A small recursive-descent parser —
// never eval() — over + − × ÷, parentheses, unary minus and postfix %.
// Percent works like a handheld calculator: "a + b%" / "a − b%" add or take off
// b percent OF a (200 − 15% = 170); anywhere else b% is b/100 (200 × 15% = 30).
// Money-ish input is tolerated: "$" and thousands commas are ignored, and the
// display glyphs (× ÷ −) are accepted alongside * / -.

export type CalcResult = { ok: true; value: number } | { ok: false; error: string };

const normalize = (expression: string): string => {
    return expression
        .replace(/[×xX]/g, "*")
        .replace(/÷/g, "/")
        .replace(/[−–]/g, "-")
        .replace(/[$,\s]/g, "");
};

class Parser {

    private readonly input: string;
    private position = 0;

    constructor(input: string) {
        this.input = input;
    }

    parse(): number {
        const value = this.sum();
        if (this.position < this.input.length) {
            throw new Error(`Unexpected "${this.input[this.position]}"`);
        }
        return value;
    }

    private peek(): string {
        return this.input[this.position] ?? "";
    }

    private sum(): number {
        let value = this.product();
        while (this.peek() === "+" || this.peek() === "-") {
            const operator = this.input[this.position++];
            const start = this.position;
            let right = this.product();
            if (this.isBarePercent(start)) {
                right = value * right;
            }
            value = operator === "+" ? value + right : value - right;
        }
        return value;
    }

    // True when the term parsed since `start` was a lone "n%" — the operand
    // that "a ± n%" scales by a.
    private isBarePercent(start: number): boolean {
        return /^(\d+\.?\d*|\.\d+)%$/.test(this.input.slice(start, this.position));
    }

    private product(): number {
        let value = this.unary();
        while (this.peek() === "*" || this.peek() === "/") {
            const operator = this.input[this.position++];
            const right = this.unary();
            if (operator === "/" && right === 0) {
                throw new Error("Division by zero");
            }
            value = operator === "*" ? value * right : value / right;
        }
        return value;
    }

    private unary(): number {
        if (this.peek() === "-") {
            this.position++;
            return -this.unary();
        }
        if (this.peek() === "+") {
            this.position++;
            return this.unary();
        }
        return this.postfix();
    }

    private postfix(): number {
        let value = this.primary();
        while (this.peek() === "%") {
            this.position++;
            value = value / 100;
        }
        return value;
    }

    private primary(): number {
        if (this.peek() === "(") {
            this.position++;
            const value = this.sum();
            if (this.peek() !== ")") {
                throw new Error("Missing )");
            }
            this.position++;
            return value;
        }
        const match = /^(\d+\.?\d*|\.\d+)/.exec(this.input.slice(this.position));
        if (!match) {
            throw new Error(this.position >= this.input.length ? "Incomplete expression" : `Unexpected "${this.peek()}"`);
        }
        this.position += match[0].length;
        return Number(match[0]);
    }

}

export function evaluate(expression: string): CalcResult {
    const input = normalize(expression);
    if (input === "") {
        return { ok: false, error: "" };
    }
    try {
        const value = new Parser(input).parse();
        if (!Number.isFinite(value)) {
            return { ok: false, error: "Not a number" };
        }
        // Round away binary noise (0.1 + 0.2) without hiding real precision.
        return { ok: true, value: Number(value.toPrecision(12)) };
    } catch (reported: unknown) {
        return { ok: false, error: reported instanceof Error ? reported.message : String(reported) };
    }
}

/** Plain, copy-pasteable rendering (no grouping), so a result can be fed back into an expression. */
export function formatResult(value: number): string {
    return String(value);
}
