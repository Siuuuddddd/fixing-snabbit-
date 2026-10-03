from __future__ import annotations

import re
import textwrap
from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw, ImageFont
from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
ASSETS = DOCS / "walkthrough-assets"
OUT = DOCS / "Snabbit-Backend-Walkthrough.docx"
SOURCE = DOCS / "Snabbit-Backend-Walkthrough-source.md"

NAVY = "16324F"
BLUE = "2E6F9E"
LIGHT = "EAF2F8"
PALE = "F6F8FA"
GRAY = "667085"
WHITE = "FFFFFF"


def font(size: int, bold: bool = False):
    candidates = [
        Path("C:/Windows/Fonts/aptos.ttf"),
        Path("C:/Windows/Fonts/arial.ttf"),
    ]
    bold_candidates = [Path("C:/Windows/Fonts/aptos-bold.ttf"), Path("C:/Windows/Fonts/arialbd.ttf")]
    pool = bold_candidates if bold else candidates
    for path in pool:
        if path.exists():
            return ImageFont.truetype(str(path), size)
    return ImageFont.load_default()


def wrap(draw: ImageDraw.ImageDraw, text: str, fnt, max_width: int) -> list[str]:
    words, lines, current = text.split(), [], ""
    for word in words:
        candidate = f"{current} {word}".strip()
        if draw.textbbox((0, 0), candidate, font=fnt)[2] <= max_width:
            current = candidate
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def draw_box(draw, xy, title, body, fill, outline=NAVY):
    x1, y1, x2, y2 = xy
    draw.rounded_rectangle(xy, radius=16, fill=fill, outline=f"#{outline}", width=3)
    tf, bf = font(23, True), font(17)
    draw.text(((x1 + x2) / 2, y1 + 18), title, font=tf, fill=f"#{NAVY}", anchor="ma")
    y = y1 + 55
    for line in wrap(draw, body, bf, x2 - x1 - 28):
        draw.text(((x1 + x2) / 2, y), line, font=bf, fill="#243B53", anchor="ma")
        y += 23


def arrow(draw, start, end, label="", dashed=False):
    x1, y1 = start
    x2, y2 = end
    if dashed:
        steps = 14
        for i in range(0, steps, 2):
            a, b = i / steps, min((i + 1) / steps, 1)
            draw.line((x1 + (x2-x1)*a, y1 + (y2-y1)*a, x1 + (x2-x1)*b, y1 + (y2-y1)*b), fill=f"#{NAVY}", width=3)
    else:
        draw.line((x1, y1, x2, y2), fill=f"#{NAVY}", width=3)
    import math
    angle = math.atan2(y2-y1, x2-x1)
    for delta in (2.65, -2.65):
        draw.line((x2, y2, x2 + 14*math.cos(angle+delta), y2 + 14*math.sin(angle+delta)), fill=f"#{NAVY}", width=3)
    if label:
        draw.text(((x1+x2)/2, (y1+y2)/2-12), label, font=font(15, True), fill=f"#{GRAY}", anchor="ms")


def architecture_diagram(path: Path):
    img = Image.new("RGB", (1800, 1040), "white")
    d = ImageDraw.Draw(img)
    d.text((900, 35), "SNABBIT BACKEND — IMPLEMENTED SERVICE TOPOLOGY", font=font(34, True), fill=f"#{NAVY}", anchor="ma")
    draw_box(d, (620, 90, 1180, 200), "API Gateway", "JWT validation • role routing • Redis rate limits • trusted identity headers", "#E8EEF4")
    names = [
        ("User", "Profiles, addresses, household, Plus, customer ratings", "#D9F2E3"),
        ("Booking", "Conductor: matching, holds, lifecycle, tracking", "#FFF0B8"),
        ("Worker", "Skills, schedule, compatibility, tier, location", "#E7DDF8"),
        ("Wallet", "Ledger, reservations, Razorpay, refunds", "#D7F2F5"),
        ("Support", "Booking-bound durable live chat", "#FFE4C2"),
        ("Notification", "History, WebSocket, push/SMS delivery", "#FFDDE8"),
    ]
    xs = [45, 340, 635, 930, 1225, 1520]
    for x, (title, body, fill) in zip(xs, names):
        draw_box(d, (x, 300, x+245, 455), title, body, fill)
        arrow(d, (900, 200), (x+122, 300), "HTTP")
    draw_box(d, (120, 545, 520, 690), "PostgreSQL per owner", "User • Booking • Worker • Wallet • Support • Notification", "#F0F3F5")
    draw_box(d, (700, 545, 1100, 690), "Kafka", "booking-events • wallet-events • user-events • worker-events", "#F8D5E4")
    draw_box(d, (1280, 545, 1680, 690), "Redis", "OTP challenges • distributed gateway token buckets", "#F0F3F5")
    for x in xs:
        arrow(d, (x+122, 455), (320 if x < 700 else 900 if x < 1200 else 1480, 545), "state" if x < 700 else "events" if x < 1200 else "cache", dashed=x >= 700 and x < 1200)
    draw_box(d, (420, 800, 780, 945), "Razorpay", "Order lookup/create • signed capture webhook • refund", "#E8EEF4")
    draw_box(d, (1020, 800, 1380, 945), "Delivery providers", "FCM HTTP v1 • Twilio SMS • Google geocoding", "#E8EEF4")
    arrow(d, (1050, 455), (600, 800), "provider API")
    arrow(d, (1642, 455), (1200, 800), "provider API")
    img.save(path)


def sequence_diagram(path: Path, title: str, actors: list[str], steps: list[tuple[int, int, str]], notes: list[str] | None = None):
    width = max(1500, 260 * len(actors))
    height = 180 + 78 * len(steps) + (100 if notes else 0)
    img = Image.new("RGB", (width, height), "white")
    d = ImageDraw.Draw(img)
    d.text((width/2, 28), title, font=font(31, True), fill=f"#{NAVY}", anchor="ma")
    margin = 100
    gap = (width - 2*margin) / max(1, len(actors)-1)
    xs = [margin+i*gap for i in range(len(actors))]
    for x, actor in zip(xs, actors):
        d.rounded_rectangle((x-95, 70, x+95, 120), radius=10, fill="#EAF2F8", outline=f"#{BLUE}", width=2)
        d.text((x, 95), actor, font=font(17, True), fill=f"#{NAVY}", anchor="mm")
        d.line((x, 120, x, height-45), fill="#98A2B3", width=2)
    for idx, (src, dst, label) in enumerate(steps):
        y = 165 + idx*78
        if src == dst:
            d.line((xs[src], y, xs[src]+75, y, xs[src]+75, y+32, xs[src], y+32), fill=f"#{NAVY}", width=3)
            d.text((xs[src]+82, y+7), label, font=font(16), fill="#243B53", anchor="lm")
        else:
            arrow(d, (xs[src], y), (xs[dst], y), label)
    if notes:
        y = height-72
        d.rounded_rectangle((80, y-18, width-80, height-25), radius=10, fill="#FFF7D6", outline="#D9A514", width=2)
        d.text((100, y), " • ".join(notes), font=font(15), fill="#594B00")
    img.save(path)


def make_diagrams():
    ASSETS.mkdir(parents=True, exist_ok=True)
    architecture_diagram(ASSETS / "architecture.png")
    diagrams = [
        ("otp-login.png", "OTP login", ["Android", "Gateway", "User/Worker", "Redis", "SMS"], [(0,1,"request OTP"),(1,2,"public auth route"),(2,3,"cooldown + HMAC hash"),(2,4,"deliver 6-digit OTP"),(0,1,"verify OTP"),(1,2,"verify"),(2,3,"attempt + compare + delete"),(2,0,"HS256 JWT with role")], ["Unknown identities receive a generic response", "Customer, worker and staff flows are role-separated"]),
        ("add-money.png", "Add Money through Razorpay", ["Customer", "Wallet", "Postgres", "Razorpay", "Kafka"], [(0,1,"POST top-up + idempotency key"),(1,2,"lock wallet; create CREATING_ORDER"),(1,3,"find/create order by receipt"),(1,2,"attach order; PENDING"),(3,1,"signed payment.captured webhook"),(1,2,"claim webhook; append CREDIT + outbox"),(1,4,"WalletTopUpSucceeded")], ["The client success screen never credits money", "Duplicate webhooks are harmless"]),
        ("booking-create.png", "Booking creation and payment", ["Customer", "Booking", "User", "Worker", "Wallet", "Postgres"], [(0,1,"availability / match-and-hold"),(1,2,"verified address + household + Plus"),(1,3,"compatible ranked candidates"),(1,2,"reserve Plus pass"),(1,5,"lock day capacity; save all holds/offers"),(3,1,"accept every offer"),(1,4,"authoritative payment request"),(4,5,"reserve wallet / create online order"),(4,1,"WalletPaymentSucceeded"),(1,5,"confirm every hold")], ["Multi-day is all-or-nothing", "Five-minute durable hold; no network call inside the hold transaction"]),
        ("worker-job.png", "Worker decision and job progress", ["Worker app", "Gateway", "Booking", "Worker", "Kafka"], [(0,1,"list privacy-filtered offers"),(1,2,"worker identity header"),(0,2,"accept or reject"),(2,4,"decision fact"),(0,3,"post fresh location"),(0,2,"arrive with accurate coordinates"),(2,2,"validate within 200 m"),(0,2,"start → complete"),(2,4,"lifecycle facts")], ["Exact address appears only within two hours", "Completion closes the booking when every visit completes"]),
        ("cancel-refund.png", "Cancellation and refund", ["Customer/Staff", "Booking", "Kafka", "Wallet", "Razorpay"], [(0,1,"quote / cancel"),(1,1,"validate owner, state, fee policy"),(1,2,"customer/platform cancellation fact"),(2,3,"terminate payment idempotently"),(3,3,"split retained fee and refund sources"),(3,4,"online refund with durable refund UUID"),(3,3,"wallet CREDIT + completion outbox")], ["Platform failure refunds the full charge", "Customer fee is retained from online funds first"]),
        ("booking-expiry.png", "Booking and offer expiry", ["Scheduler", "Booking DB", "Booking", "Kafka", "Wallet/User"], [(0,1,"scan expired offer/hold IDs"),(0,2,"process each under row locks"),(2,1,"expire offers; release/expire all holds"),(2,3,"WorkerOfferExpired / BookingExpired"),(3,4,"release Plus pass; terminate payment")], ["Batches are bounded", "Expiry state is durable and replay-safe"]),
        ("plus.png", "Plus subscription and pass lifecycle", ["Customer", "User", "Wallet", "Razorpay", "Kafka", "Booking"], [(0,1,"select server-owned plan"),(1,2,"create Plus purchase"),(2,3,"provider order"),(3,2,"signed capture"),(2,4,"PlusSubscriptionPurchased"),(4,1,"activate + grant passes"),(5,1,"reserve pass during matching"),(4,1,"consume/release/restore from booking facts")], ["Purchase capture does not credit wallet balance", "Early renewal extends current expiry"]),
        ("notifications.png", "Outbox to notification delivery", ["Producer DB", "Outbox publisher", "Kafka", "Notification", "Delivery job", "FCM/Twilio"], [(0,0,"business row + outbox in one transaction"),(1,0,"lease unpublished rows"),(1,2,"publish with aggregate key"),(2,3,"retry/DLT listener + dedupe"),(3,3,"store history + delivery rows"),(4,3,"lease pending delivery"),(4,5,"push; critical also SMS"),(4,3,"delivered / retry / DEAD")], ["WebSocket history resumes from durable IDs", "Local profiles use logging adapters"]),
        ("support.png", "Live support", ["Customer", "Gateway", "Support", "Booking", "Postgres", "Staff"], [(0,1,"open ticket for booking"),(1,2,"trusted customer identity"),(2,3,"verify booking ownership"),(2,4,"ticket + first message"),(0,2,"SSE from afterMessageId"),(5,2,"staff list / message / resolve"),(2,4,"append messages; update ticket")], ["The current implementation is durable SSE chat", "No third-party help-desk integration"]),
    ]
    for filename, title, actors, steps, notes in diagrams:
        sequence_diagram(ASSETS / filename, title, actors, steps, notes)


def shade(cell, fill):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:fill"), fill)
    tcPr.append(shd)


def margins(cell, top=80, start=90, bottom=80, end=90):
    tc = cell._tc
    tcPr = tc.get_or_add_tcPr()
    tcMar = tcPr.first_child_found_in("w:tcMar")
    if tcMar is None:
        tcMar = OxmlElement("w:tcMar")
        tcPr.append(tcMar)
    for tag, value in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        node = OxmlElement(f"w:{tag}")
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")
        tcMar.append(node)


def set_repeat_header(row):
    trPr = row._tr.get_or_add_trPr()
    tblHeader = OxmlElement("w:tblHeader")
    tblHeader.set(qn("w:val"), "true")
    trPr.append(tblHeader)


def keep_row_together(row):
    trPr = row._tr.get_or_add_trPr()
    cant_split = OxmlElement("w:cantSplit")
    trPr.append(cant_split)


def set_cell_width(cell, inches):
    cell.width = Inches(inches)
    tcPr = cell._tc.get_or_add_tcPr()
    tcW = tcPr.first_child_found_in("w:tcW")
    if tcW is None:
        tcW = OxmlElement("w:tcW")
        tcPr.append(tcW)
    tcW.set(qn("w:w"), str(int(inches * 1440)))
    tcW.set(qn("w:type"), "dxa")


def add_table(doc, headers: list[str], rows: Iterable[Iterable[str]], widths: list[float] | None = None):
    rows = list(rows)
    table = doc.add_table(rows=1, cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.style = "Table Grid"
    table.autofit = False
    for i, header in enumerate(headers):
        cell = table.rows[0].cells[i]
        cell.text = header
        shade(cell, NAVY)
        margins(cell)
        for run in cell.paragraphs[0].runs:
            run.font.color.rgb = RGBColor(255,255,255)
            run.bold = True
            run.font.size = Pt(8)
        if widths:
            set_cell_width(cell, widths[i])
    set_repeat_header(table.rows[0])
    keep_row_together(table.rows[0])
    for ridx, row in enumerate(rows):
        cells = table.add_row().cells
        for i, value in enumerate(row):
            cells[i].text = str(value)
            cells[i].vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.TOP
            margins(cells[i])
            if ridx % 2:
                shade(cells[i], PALE)
            for p in cells[i].paragraphs:
                p.paragraph_format.space_after = Pt(0)
                for run in p.runs:
                    run.font.size = Pt(7.5)
        if widths:
            for cell, w in zip(cells, widths):
                set_cell_width(cell, w)
        keep_row_together(table.rows[-1])
    doc.add_paragraph().paragraph_format.space_after = Pt(2)
    return table


def add_heading(doc, text, level=1):
    p = doc.add_heading(text, level=level)
    p.paragraph_format.keep_with_next = True
    return p


def add_body(doc, text: str):
    for block in textwrap.dedent(text).strip().split("\n\n"):
        p = doc.add_paragraph(block.replace("\n", " "))
        p.paragraph_format.space_after = Pt(5)
    return p


def add_bullets(doc, items: Iterable[str]):
    for item in items:
        p = doc.add_paragraph(style="List Bullet")
        p.add_run(item)
        p.paragraph_format.space_after = Pt(2)


def add_figure(doc, filename: str, caption: str, width=6.8):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.keep_with_next = True
    picture = p.add_run().add_picture(str(ASSETS / filename), width=Inches(width))
    picture._inline.docPr.set("descr", caption)
    picture._inline.docPr.set("title", caption.split(".", 1)[0])
    c = doc.add_paragraph(caption, style="Caption")
    c.alignment = WD_ALIGN_PARAGRAPH.CENTER
    c.paragraph_format.keep_with_next = True


def add_code(doc, code: str):
    p = doc.add_paragraph()
    p.style = doc.styles["No Spacing"]
    p.paragraph_format.left_indent = Inches(.25)
    p.paragraph_format.right_indent = Inches(.25)
    p.paragraph_format.space_after = Pt(6)
    shade_paragraph(p, "F2F4F7")
    r = p.add_run(code)
    r.font.name = "Consolas"
    r.font.size = Pt(8)


def shade_paragraph(p, fill):
    pPr = p._p.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:fill"), fill)
    pPr.append(shd)


def page_break(doc):
    doc.add_page_break()


def all_kotlin_files(service: str):
    return sorted((ROOT / service / "src/main/kotlin").rglob("*.kt"))


def class_inventory(service: str):
    categories = ("controller", "service", "domain", "repository", "events", "event", "scheduler", "client", "matching", "payment", "tracking", "auth", "rating", "location", "delivery", "security", "staff", "plus", "booking")
    records = []
    for path in all_kotlin_files(service):
        rel = path.relative_to(ROOT).as_posix()
        if "/dto/" in rel or "/exception/" in rel or "/domain/enums/" in rel:
            continue
        text = path.read_text(encoding="utf-8")
        classes = re.findall(r"(?m)^\s*(?:data\s+|sealed\s+)?(?:class|interface|object)\s+(\w+)", text)
        if not classes:
            continue
        methods = []
        for m in re.finditer(r"(?m)^\s*(?!private\s)(?:override\s+)?(?:suspend\s+)?fun\s+(\w+)\s*\(([^)]*)\)", text):
            signature = re.sub(r"\s+", " ", f"{m.group(1)}({m.group(2).strip()})")
            methods.append(signature[:180])
        records.append((rel, classes, methods))
    return records


def method_explanation(class_name: str, signature: str, path: str) -> str:
    name = signature.split("(", 1)[0]
    custom = {
        "requestOtp": "I normalize the Indian E.164 phone number, enforce the Redis cooldown, store only an HMAC of the generated OTP, and deliver it only for an active registered identity.",
        "verifyOtp": "I limit attempts, compare the supplied OTP in constant time, destroy the challenge after success or exhaustion, and issue a short-lived HS256 JWT with the correct role.",
        "matchAndHold": "I load the verified address and household context concurrently, obtain compatible candidates for every visit, reserve an eligible Plus pass, and ask the booking transaction to hold every visit atomically. Idempotent replays return the winning booking.",
        "availability": "I validate the booking window and return candidate counts and lowest prices without writing a hold or reserving a pass.",
        "createBookingPayment": "I retrieve Booking's authoritative quote, reject stale or wrong-state bookings, reserve the wallet portion under lock, and create a provider order only for the online remainder.",
        "processCapture": "I durably claim the provider webhook, match it to a top-up, Plus purchase, or booking payment, validate the amount, and commit ledger/state plus outbox facts exactly once. Late captures queue compensation refunds.",
        "balance": "I derive ledger balance, active reservations, available balance, and total spent from durable rows; I do not trust a mutable balance column.",
        "executeRefund": "I claim a durable refund with a lease, call Razorpay only for the online portion, append any wallet credit, and return failed attempts to the retry queue.",
        "acceptOffer": "I lock the booking and offer, validate worker ownership and deadline, accept idempotently, and move the booking to payment only after every visit offer is accepted.",
        "rejectOffer": "I lock and reject the offer, close remaining offers, release all holds, mark the booking unavailable, and append the rejection fact.",
        "confirmPayment": "I confirm only an awaiting-payment booking with live holds; otherwise I expire the booking. Success confirms every hold and appends BookingConfirmed.",
        "workerArrive": "I require the assigned worker, a confirmed visit, GPS accuracy within the configured threshold, and a point within 200 metres of the verified service location before recording arrival.",
        "workerStart": "I move an arrived visit into progress under a booking lock and emit ServiceStarted only when the state changes.",
        "workerComplete": "I complete the assigned visit idempotently and complete the parent booking only when every visit has completed.",
        "publishAvailableEvents": "I lease a bounded outbox batch, publish each versioned envelope with its aggregate ID as the Kafka key, acknowledge only after Kafka success, and release failures for retry.",
        "handle": "I validate and deduplicate the versioned event, apply only the event types owned by this handler, and commit the local side effect in the same transaction as the processed-message claim.",
        "deliverPending": "I lease pending delivery rows, resolve push tokens or phone contact, call the configured adapter, and either acknowledge, retry with backoff, or mark the delivery dead after the attempt limit.",
        "stream": "I expose a resumable live stream backed by durable database IDs, so reconnecting clients continue strictly after their last cursor.",
    }
    if name in custom:
        return custom[name]
    if "Repository" in class_name:
        return f"I use `{name}` as a Spring Data query or lock boundary; the signature defines the owned-table lookup and callers handle absence or conflicts explicitly."
    if "/domain/" in path:
        return f"I use `{name}` to enforce an aggregate state transition or derived rule inside the entity before persistence; invalid transitions fail before an event is emitted."
    if "Controller" in class_name:
        return f"I use `{name}` as a thin HTTP boundary: it reads validated path/header/body inputs, relies on trusted gateway identity where applicable, delegates to the service layer, and returns the service snapshot."
    if "Client" in class_name or "Gateway" in class_name:
        return f"I use `{name}` for an internal or provider call with configuration-owned base URLs and service identity; transport/provider failures are translated or propagated to the owning workflow."
    if "Filter" in class_name:
        return f"I use `{name}` to enforce the security boundary before business code runs, rejecting missing or mismatched identity rather than trusting caller-supplied headers."
    if "Store" in class_name:
        return f"I use `{name}` for a narrow durable claim/lease/update operation so concurrent instances coordinate through PostgreSQL rather than process memory."
    return f"I use `{name}` to execute this class's named responsibility with the arguments shown in the signature; validations and database/event side effects stay in the owning service."


APIS = {
"API Gateway": [
("ALL", "/api/v1/**", "Role varies by route", "Bearer JWT except public auth/webhook/health", "Proxied service response", "Validates issuer/signature/role, replaces identity headers, applies Redis token bucket and routes.", "401/403, 429, route unavailable"),
],
"User Service": [
("POST","/api/v1/auth/otp/request","Public","phoneNumber","status, expiresInSeconds, optional debugOtp","Requests customer OTP with anti-enumeration response.","400, 429"),
("POST","/api/v1/auth/otp/verify","Public","phoneNumber, otp","accessToken, tokenType, expiresInSeconds","Verifies OTP and issues CUSTOMER JWT.","400 invalid OTP"),
("POST","/api/v1/staff-auth/otp/request","Public","phoneNumber","OTP request result","Requests OTP for registered active staff.","400, 429"),
("POST","/api/v1/staff-auth/otp/verify","Public","phoneNumber, otp","access token","Issues role-specific staff JWT.","400 invalid OTP"),
("GET","/api/v1/users/{userId}/addresses","Customer owner","—","address snapshots","Lists owned addresses.","403, 404"),
("POST","/api/v1/users/{userId}/addresses","Customer owner","label, addressLine, city, pincode, coordinates, accuracy, capturedAt","verified address","Validates fresh accurate Mumbai location and geocoding policy.","400/422, 403, 404"),
("DELETE","/api/v1/users/{userId}/addresses/{addressId}","Customer owner","—","204","Deletes owned address and writes user event.","403, 404"),
("GET/PUT","/api/v1/users/{userId}/household","Customer owner","household fields on PUT","household snapshot","Reads or replaces normalized compatibility preferences.","403, 404"),
("POST/DELETE","/api/v1/users/{userId}/household/favourites/{workerId}","Customer owner","—","household snapshot","Adds/removes ordered favourite; maximum three.","403, 404, 409"),
("GET","/api/v1/users/{userId}/plus","Customer owner","—","active, expiry, availablePasses","Returns derived Plus status.","403, 404"),
("POST","/api/v1/users/{userId}/plus/purchases","Customer owner","walletId, planCode; Idempotency-Key","Plus purchase snapshot","Uses server-owned plan terms and Wallet provider order.","400, 403, 409, 503"),
("POST","/api/v1/worker/ratings/customers/{customerId}","Worker","bookingId, visitIndex, stars, comment","customer rating snapshot","Rates the customer for worker's completed visit exactly once.","400, 403, 409"),
("GET","/internal/v1/users/{userId}/addresses/{addressId}","Service token","—","verified address","Authoritative address ownership lookup.","401, 403, 404"),
("GET","/internal/v1/users/{userId}/household","Service token","—","household snapshot","Booking matching context.","401, 404"),
("GET","/internal/v1/users/{userId}/contact","Service token","—","userId, phoneNumber","Notification contact lookup.","401, 404"),
("POST","/internal/v1/users/{userId}/plus/subscriptions","Service token","source/timing/pass terms","Plus status","Internal idempotent activation hook.","400, 401, 409"),
("POST","/internal/v1/users/{userId}/plus/passes/reservations","Service token","bookingId, serviceCode, grossTotalMinor, holdUntil","pass reservation","Reserves one eligible pass under lock.","400, 401"),
("POST","/internal/v1/users/{userId}/plus/passes/reservations/{bookingId}/consume|release","Service token","confirmedAt for consume","empty","Moves held pass to used or available.","400, 401"),
],
"Booking Service": [
("GET","/api/v1/services","Customer","—","serviceId/code/name/base rate","Returns active server-owned catalogue.","401"),
("POST","/api/v1/bookings/availability","Customer","service, type, duration, address, startsAt","per-visit availability","Read-only compatible-worker preview.","400, 401, 409"),
("POST","/api/v1/bookings/match-and-hold","Customer","same + Idempotency-Key","booking snapshot","Matches workers, reserves Plus pass and atomically holds all visits.","400, 401, 409"),
("GET","/api/v1/bookings/{bookingId}","Customer owner","—","booking snapshot","Returns authoritative lifecycle and visits.","403, 404"),
("DELETE","/api/v1/bookings/{bookingId}/hold","Customer owner","—","booking snapshot","Cancels only pre-confirmation booking and releases capacity.","403, 404, 409"),
("POST","/api/v1/bookings/{bookingId}/payments","Customer owner","walletId, useWallet + Idempotency-Key","payment snapshot","Starts split payment from authoritative quote.","400, 403, 409, 503"),
("GET","/api/v1/bookings/{bookingId}/cancellation-quote","Customer owner","—","fee/refund/policy band","Calculates current Plus-aware fee.","403, 404, 409"),
("POST","/api/v1/bookings/{bookingId}/cancellations","Customer owner","optional reason","booking snapshot","Cancels unstarted confirmed booking and publishes refund amount.","400, 403, 409"),
("GET","/api/v1/worker/bookings/offers","Worker","—","privacy-filtered offers","Lists unexpired offers assigned to worker.","401/403"),
("GET","/api/v1/worker/bookings/jobs","Worker","—","active jobs","Reveals exact address only in two-hour window.","401/403"),
("POST","/api/v1/worker/bookings/{bookingId}/offers/{offerId}/accept|reject","Worker owner","—","booking snapshot","Locks and records decision; reject releases atomic booking.","403, 404, 409"),
("POST","/api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/arrive","Assigned worker","lat/lon/accuracy","booking snapshot","Validates service proximity and marks arrived.","400, 403, 409"),
("POST","/api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/start|complete","Assigned worker","—","booking snapshot","Advances ordered job lifecycle idempotently.","403, 409"),
("GET","/api/v1/bookings/{bookingId}/visits/{visitIndex}/worker-location[/stream]","Customer owner","cursor for stream","snapshot/SSE","Returns only fresh location inside privacy window.","403, 404, 409, 503"),
("POST","/api/v1/staff/operations/bookings/{bookingId}/platform-cancellation","Operations/Support staff","reason","booking snapshot","Full-refund cancellation before service starts; audits staff ID.","403, 404, 409"),
("GET/POST","/internal/v1/bookings/**","Service token","context-specific","authoritative contexts/snapshots","Ownership, rating, Plus, payment quote, create and confirmation APIs.","401, 404, 409"),
],
"Worker Service": [
("POST","/api/v1/worker-auth/otp/request|verify","Public","phone; phone+otp","OTP result / WORKER JWT","Role-separated worker authentication.","400, 429"),
("GET","/api/v1/workers/me/availability","Worker","from, to","availability rows","Lists worker's bounded schedule.","400, 404"),
("PUT","/api/v1/workers/me/availability/{serviceDate}","Worker","status, from, until","availability row","Upserts own day and publishes worker event.","400, 404"),
("POST","/api/v1/workers/me/location","Worker","coordinates, accuracy, capturedAt","location snapshot","Stores fresh accurate current point plus history.","400, 404"),
("POST","/api/v1/ratings/workers/{workerId}","Customer","bookingId, visitIndex, stars, comment","worker rating snapshot","Rates assigned worker for owned completed visit once.","400, 403, 409"),
("POST","/internal/v1/workers/matches","Service token","service/window/location/constraints/favourites","ranked matches","Hard-filters compatibility, then ranks favourite/rating/distance and prices tier.","400, 401"),
("GET","/internal/v1/workers/{workerId}/location|contact","Service token","—","latest location/contact","Authoritative tracking/delivery lookup.","401, 404"),
],
"Wallet Service": [
("GET","/api/v1/wallets/{walletId}","Customer owner","—","derived balance","Returns ledger, reserved, available and total spent.","403, 404"),
("POST","/api/v1/wallets/{walletId}/top-ups","Customer owner","amountMinor + Idempotency-Key","top-up/provider order","Creates or recovers Razorpay order; does not credit yet.","400, 403, 409, 502"),
("POST","/webhooks/razorpay","Razorpay signature","raw body + provider headers","acknowledgement","Verifies raw signature and durably processes captured payments once.","400 invalid signature/payload, 409"),
("GET","/api/v1/staff/finance/refunds","Finance","optional status","refund rows","Lists recent durable refund queue.","403"),
("POST","/api/v1/staff/finance/refunds/{refundId}/retry","Finance","—","audit action + refund","Retries existing refund and records staff outcome.","403, 404, 409, 502"),
("GET/POST","/internal/v1/wallets/**","Service token","purchase/payment/refund commands","snapshots","Ownership, Plus order, split payment, refund and cancel APIs.","401, 404, 409, 502"),
],
"Support Service": [
("POST","/api/v1/support/tickets","Customer","bookingId, subject, initialMessage","ticket snapshot","Verifies booking ownership and opens/replays one ticket per booking.","400, 403, 404"),
("POST","/api/v1/support/tickets/{ticketId}/messages","Customer owner","body","message snapshot","Appends to open ticket.","400, 403, 404, 409"),
("GET","/api/v1/support/tickets/{ticketId}/stream","Customer owner","afterMessageId","SSE messages","Resumable durable customer chat stream.","403, 404"),
("GET/POST","/api/v1/staff/support/**","Support staff","status/cursor/body","tickets/messages","Lists, streams, replies and resolves tickets.","403, 404, 409"),
("POST","/internal/v1/support/tickets/{ticketId}/messages|resolve","Service token","agentId/body","message or ticket","Internal agent operations.","401, 404, 409"),
],
"Notification Service": [
("GET","/api/v1/notifications","Customer","afterId","notification history","Returns up to 100 customer notifications after cursor.","401"),
("POST","/api/v1/notifications/{id}/read","Customer owner","—","notification snapshot","Marks owned notification read idempotently.","403, 404"),
("POST/DELETE","/api/v1/notifications/devices[/{deviceId}]","Customer owner","pushToken on POST","device snapshot","Activates or deactivates Android push token.","400, 403, 404"),
("GET/POST/DELETE","/api/v1/worker/notifications/**","Worker","same worker variants","history/device snapshots","Worker-isolated notification resources.","400, 403, 404"),
("WS","/ws/v1/notifications?afterId=…","Customer","durable cursor","JSON notifications","Resumable customer WebSocket stream.","401/403"),
("WS","/ws/v1/worker/notifications?afterId=…","Worker","durable cursor","JSON notifications","Resumable worker WebSocket stream.","401/403"),
],
}


EVENTS = [
("booking-events","BookingHeld","bookingId, visitCount, holdExpiresAt, quotedTotalMinor","Notification template; analytics/read models are not implemented."),
("booking-events","WorkerOffersCreated","bookingId, offers[{offerId, workerId, slotHoldId, expiresAt, requirements}]","Notification fans out one privacy-filtered worker notification per offer."),
("booking-events","WorkerOfferAccepted","bookingId, offerId, workerId","Published; no in-repo consumer beyond general notification selection."),
("booking-events","BookingAwaitingPayment","bookingId, holdExpiresAt","Notification."),
("booking-events","WorkerOfferRejected","bookingId, offerId, workerId","User Plus releases pass; notification; wallet listener ignores."),
("booking-events","WorkerOfferExpired","bookingId, offerId, workerId","User Plus releases pass; notification."),
("booking-events","BookingConfirmed","bookingId, paymentReference","User Plus consumes pass; notification."),
("booking-events","WorkerArrived","bookingId, visitIndex, workerId, arrivedAt","Notification."),
("booking-events","ServiceStarted","bookingId, visitIndex, workerId, occurredAt","Notification."),
("booking-events","ServiceCompleted","bookingId, visitIndex, workerId, occurredAt","Notification."),
("booking-events","BookingCompleted","bookingId, completedAt","Notification."),
("booking-events","BookingExpired","bookingId, optional expiredAt","Wallet terminates payment; User releases pass; notification."),
("booking-events","BookingCancelled","bookingId","Wallet terminates pending payment; User releases pass; notification."),
("booking-events","BookingCustomerCancelled","bookingId, customerId, cancellationFeeMinor, refundAmountMinor, plusActive, cancelledAt","Wallet queues specified refund; User restores pass; notification."),
("booking-events","BookingPlatformCancelled","bookingId, customerId, refundAmountMinor, cancellationFeeMinor, cancelledAt, optional cancelledByStaffId","Wallet queues full refund; User restores pass; notification."),
("wallet-events","WalletTopUpSucceeded","walletId, topUpId, amountMinor","Notification."),
("wallet-events","WalletPaymentSucceeded","bookingId, paymentId, walletAmountMinor, onlineAmountMinor","Booking confirms held visits; notification."),
("wallet-events","WalletPaymentFailed","bookingId, paymentId","Booking cancels pre-confirmation state; notification."),
("wallet-events","WalletPaymentRefundRequired","bookingId, paymentId, refundId","Notification; finance uses wallet DB/API rather than Kafka."),
("wallet-events","WalletRefundQueued","bookingId, paymentId, refundId, amountMinor, retainedFeeMinor","Notification."),
("wallet-events","WalletRefundCompleted","bookingId, paymentId, refundId, amountMinor","Notification."),
("wallet-events","PlusSubscriptionPurchased","purchaseId, walletId, customerId, planCode, amountMinor, durationDays, passCount, passDiscountMinor, serviceCode, providerPaymentId","User activates Plus; notification."),
("user-events","UserAddressCreated / UserAddressDeleted","userId, address identifiers / deletion time","Published; no in-repo consumer."),
("user-events","HouseholdPreferencesUpdated / FavouriteWorkerAdded / FavouriteWorkerRemoved","userId plus change fields","Published; no in-repo consumer."),
("user-events","PlusSubscriptionActivated / CustomerRated","userId plus subscription or rating fields","Published; no in-repo consumer."),
("worker-events","WorkerAvailabilityUpdated / WorkerLocationUpdated / WorkerRated","workerId plus schedule, location, or rating fields","Published; no in-repo consumer."),
]


DBS = {
"User": [("user_profile","customer identity, status, aggregate rating, Plus flags/expiry"),("user_address","verified Mumbai address and coordinates"),("household_preference","compatibility arrays and household facts"),("favourite_worker","ordered maximum-three favourites"),("customer_rating","one rating per booking visit"),("staff_account","staff OTP identity and role"),("plus_subscription","paid activation source and interval"),("plus_pass","available/reserved/used pass lifecycle"),("outbox_event","versioned user facts with leases"),("processed_message","consumer idempotency claims")],
"Booking": [("service_offering","active catalogue and hourly price"),("booking","customer, status, price/address/requirements snapshots, cancellation audit"),("slot_hold","visit time, worker, durable hold and job lifecycle"),("worker_offer","decision deadline/status per visit"),("worker_day_capacity","worker/date capacity row locked during holds"),("outbox_event","versioned booking facts with leases"),("processed_message","wallet-event dedupe")],
"Worker": [("worker_profile","identity, tier, service radius, current point, rating aggregate"),("worker_skill","enabled service codes"),("worker_availability","per-day window and status"),("worker_compatibility","pets/food/language/allergy/equipment/accessibility capabilities"),("worker_location_history","append-only accurate location points"),("worker_rating","one customer rating per booking visit"),("outbox_event","versioned worker facts with leases"),("processed_message","consumer idempotency storage")],
"Wallet": [("wallet_account","wallet ownership and currency; no mutable balance"),("wallet_ledger_entry","append-only CREDIT/DEBIT money movements"),("wallet_reservation","active/captured/released/expired wallet holds"),("top_up_order","provider top-up order lifecycle"),("booking_payment","wallet/online split and provider references"),("payment_refund","durable split refund and processing lease"),("plus_purchase","provider-paid Plus order; not wallet credit"),("processed_webhook","durable Razorpay inbox and result"),("finance_refund_action","staff retry audit"),("outbox_event","versioned wallet facts with leases"),("processed_message","booking-event dedupe")],
"Support": [("support_ticket","one booking-bound customer ticket and status"),("support_message","monotonic durable chat messages")],
"Notification": [("notification","customer/worker history with durable numeric cursor"),("device_registration","recipient-scoped Android push tokens"),("notification_delivery","leased PUSH/SMS attempts and terminal status"),("processed_message","Kafka event dedupe")],
}


def build_doc():
    make_diagrams()
    doc = Document()
    sec = doc.sections[0]
    sec.page_width, sec.page_height = Inches(8.5), Inches(11)
    sec.top_margin = sec.bottom_margin = Inches(.65)
    sec.left_margin = sec.right_margin = Inches(.65)
    styles = doc.styles
    styles["Normal"].font.name = "Aptos"
    styles["Normal"].font.size = Pt(9.3)
    styles["Normal"].font.color.rgb = RGBColor(36,59,83)
    for level, size in ((1,18),(2,14),(3,11)):
        st = styles[f"Heading {level}"]
        st.font.name = "Aptos Display"
        st.font.size = Pt(size)
        st.font.bold = True
        st.font.color.rgb = RGBColor(22,50,79)
        st.paragraph_format.space_before = Pt(10 if level > 1 else 14)
        st.paragraph_format.space_after = Pt(5)
        st.paragraph_format.keep_with_next = True
    styles["Caption"].font.name = "Aptos"
    styles["Caption"].font.size = Pt(8)
    styles["Caption"].font.italic = True
    styles["Caption"].font.color.rgb = RGBColor(102,112,133)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(90)
    r = p.add_run("Snabbit Backend Walkthrough")
    r.bold = True; r.font.name = "Aptos Display"; r.font.size = Pt(32); r.font.color.rgb = RGBColor(22,50,79)
    p = doc.add_paragraph("A code-grounded technical presentation of the Kotlin/Spring Boot prototype")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(15); p.runs[0].font.color.rgb = RGBColor(46,111,158)
    doc.add_paragraph("Architecture • workflows • APIs • data • events • operations • honest status").alignment = WD_ALIGN_PARAGRAPH.CENTER
    doc.add_paragraph("Prepared from repository state on 3 October 2026").alignment = WD_ALIGN_PARAGRAPH.CENTER
    add_figure(doc, "architecture.png", "Figure 1. Implemented service topology and external dependencies.", 7.0)
    p = doc.add_paragraph("Confidentiality note: this walkthrough names configuration variables only. It contains no tokens, provider credentials, OTPs, phone numbers, or webhook secrets.")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].italic = True
    page_break(doc)

    add_heading(doc, "How I explain this backend in five minutes", 1)
    add_body(doc, """
    I built this prototype around one rule: the Booking service conducts the customer journey, but each business capability owns its own data. User owns identity, verified addresses, household compatibility, favourites and Plus entitlements. Worker owns skills, schedules, compatibility, tiers and location. Wallet owns every money decision. Booking coordinates them through internal HTTP for immediate decisions and Kafka facts for work that can complete asynchronously.

    When a customer asks for a service, I first resolve a verified Mumbai address and the household constraints from User. I ask Worker for candidates that are active, skilled, available, nearby and compatible. Worker computes the authoritative visit price as catalogue rate × duration plus one tier surcharge. For multi-day bookings I either find candidates for every requested day or create nothing.

    I prevent double booking in PostgreSQL, not Kafka or a JVM lock. Booking locks deterministic worker/day capacity rows with PESSIMISTIC_WRITE, checks overlap, and commits five-minute HELD rows for all visits in one transaction. Only after every worker offer is accepted does the customer enter payment. No database transaction stays open across User, Worker, Wallet, Razorpay or Kafka calls.

    Wallet never stores a casually mutable balance. I calculate available balance as append-only credits minus debits minus active reservations, and total spent from completed booking debits. Split payment reserves as much wallet value as allowed and creates a Razorpay order only for the remainder. A signed Razorpay capture webhook—not the Android success screen—performs the credit or capture exactly once.

    Every producer persists its business change and outbox event atomically. A leased publisher sends versioned envelopes keyed by aggregate ID. Consumers claim event IDs in their own database transaction and Spring Kafka supplies bounded retry topics and DLTs. That gives me at-least-once transport with idempotent business effects.

    This is a strong prototype, not a finished production platform. Real Razorpay, FCM, Twilio and Google geocoding adapters exist, but local defaults use fake/logging/disabled modes. Internal HTTP authentication is a shared service token rather than workload identity or mTLS. Analytics, worker re-matching after rejection, settlement/payout, and multi-region operations are not implemented.
    """)
    add_heading(doc, "Five talking points", 2)
    add_bullets(doc, [
        "Booking is the conductor; no service queries another service's database.",
        "A real PostgreSQL slot hold exists before payment, and multi-day creation is atomic.",
        "Wallet money is append-only and webhook verified; late captures become durable compensation refunds.",
        "Transactional outboxes, aggregate keys, schema versions, idempotent consumers, retry topics and DLTs make async delivery explicit.",
        "The prototype is honest about its boundary: production credentials, workload identity, observability infrastructure and several marketplace features remain deployment work.",
    ])
    page_break(doc)

    add_heading(doc, "Table of contents", 1)
    for entry in [
        "1. Overview", "2. Core workflows", "3. Service chapters", "4. API reference", "5. Method-level logic",
        "6. Cross-cutting design", "7. Data and events reference", "8. Deployment and operations", "9. Honest status and next steps",
    ]:
        doc.add_paragraph(entry, style="List Number" if False else "List Bullet")
    add_body(doc, "The document uses native Heading 1/2/3 styles, so Google Docs will populate its outline. After opening the DOCX in Google Docs, use Insert → Table of contents if you want Google to replace this static contents page with live page-number links.")
    page_break(doc)

    add_heading(doc, "1. Overview", 1)
    add_heading(doc, "What I am solving", 2)
    add_body(doc, """
    I am building the backend for an on-demand home-service application: customers can book house cleaning, dish washing, laundry and toilet cleaning as an instant visit, a scheduled visit, or a two-to-seven-visit multi-day plan. The difficult part is not a CRUD booking record. It is coordinating a scarce worker slot, household compatibility, a stable price, split money, provider callbacks, cancellations and live operational updates without letting independent services corrupt one another.

    The implemented launch boundary is Mumbai. Customer and worker records are seeded for prototype use; registration is not exposed. Customers, workers and staff authenticate with Indian phone-number OTP flows. The code supports the four catalogue services, 08:00–19:00 operating hours, one-to-four whole-hour duration, Standard/Veteran/Expert tiers, a maximum of three favourites, Plus passes and continuity, mutual ratings, live location, notifications and durable support chat.
    """)
    add_figure(doc, "architecture.png", "Figure 2. I separate ownership by service and use HTTP for immediate decisions, Kafka for facts after commit.", 7.0)
    add_heading(doc, "Why I split the services this way", 2)
    add_table(doc, ["Boundary", "What it owns", "Why"], [
        ("API Gateway", "Authentication enforcement, routing, rate limits; no business tables", "One edge policy and no duplicate trust of client identity headers."),
        ("User", "Customer/staff identity, verified addresses, household, favourites, Plus, customer ratings", "These facts change independently of bookings and are authoritative inputs to matching."),
        ("Booking", "Catalogue, booking, visits/holds, offers, job state and cancellation audit", "One conductor owns the workflow and the capacity decision."),
        ("Worker", "Worker identity, skills, availability, compatibility, tier, location and worker ratings", "Matching never reads booking/user tables and worker self-service stays isolated."),
        ("Wallet", "Accounts, append-only ledger, reservations, provider orders, payments and refunds", "Money has one owner and an auditable state machine."),
        ("Support", "Tickets and messages", "Live help can scale and retain its own durable cursor without coupling Booking."),
        ("Notification", "History, devices and delivery attempts", "Provider delivery is asynchronous and failure-tolerant."),
    ], [1.0, 2.5, 3.5])
    add_heading(doc, "Implemented interaction model", 2)
    add_bullets(doc, [
        "Synchronous internal HTTP answers decisions that the current customer request needs: address ownership, household context, worker matches, authoritative booking quotes and wallet orders.",
        "Kafka carries committed facts. It does not lock slots and is not used as a distributed transaction coordinator.",
        "Each state-owning service has its own PostgreSQL configuration and Flyway history. The compose topology starts six distinct PostgreSQL containers.",
        "Redis is used for short-lived OTP state and the gateway's distributed token bucket; it is not the source of truth for booking or money.",
        "Kotlin coroutines are used where independent I/O can overlap, such as loading address and household context and matching multiple visits; JPA work remains ordinary bounded transactions.",
    ])

    add_heading(doc, "2. Core workflows", 1)
    workflows = [
        ("Signup and OTP login", "otp-login.png", "Figure 3. Customer, worker and staff OTP authentication.", "There is no public registration endpoint in this prototype. I authenticate only seeded, active identities. I normalize an Indian E.164 number, use Redis SET-if-absent for the request cooldown, generate a six-digit OTP, store only an HMAC keyed by OTP_PEPPER, and deliver through the configured SMS gateway. An unknown number receives the same generic response but no challenge, which reduces account enumeration. Verification counts attempts, compares hashes in constant time, deletes the challenge and issues a short-lived HS256 JWT containing the role. The gateway validates issuer, signature and role before replacing any inbound identity headers."),
        ("Add Money via Razorpay", "add-money.png", "Figure 4. Wallet top-up: order first, ledger credit only after a verified capture.", "When the customer submits amountMinor and an Idempotency-Key, I lock the wallet, validate whole-rupee input, and create a CREATING_ORDER top-up. I first look up a Razorpay order by the deterministic receipt and create it only if absent; this recovers ambiguous provider timeouts. The record becomes PENDING when the provider order matches amount and currency. Later, the raw webhook body is verified with its signature, the provider event is durably claimed, and payment.captured appends one CREDIT ledger row and one outbox fact. Client-side payment success is never authoritative."),
        ("Booking creation: match, hold and pay", "booking-create.png", "Figure 5. Booking orchestration, from compatibility to confirmed capacity.", "I validate instant/scheduled/multi-day windows and the one-to-four-hour duration before matching. I fetch the verified address and household in parallel, then request candidates for every visit in parallel. Worker hard-filters compatibility and ranks remaining candidates. Plus tries one common favourite across every multi-day visit; otherwise I take the best candidate per visit. I reserve a pass, then Booking locks worker/day capacity rows in a deterministic order, checks overlaps and commits every booking/hold/offer/outbox row atomically. All-or-nothing is the implemented multi-day policy. Payment begins only after all offers are accepted and uses the stored quote, not an Android amount."),
        ("Worker accept/decline and job progress", "worker-job.png", "Figure 6. Worker offer and job lifecycle.", "A worker sees only their unexpired offers, service context and household requirements—no customer identity or address. Accept locks both booking and offer; the last accepted visit moves the booking to AWAITING_PAYMENT. A single rejection closes all outstanding offers and releases the whole atomic booking; automatic re-matching is not implemented. Once payment confirms the booking, assigned jobs reveal the exact address only within two hours. Arrival validates accurate GPS within 200 metres, then the only valid progression is ASSIGNED → ARRIVED → IN_PROGRESS → COMPLETED."),
        ("Cancellation and refund", "cancel-refund.png", "Figure 7. Cancellation records the policy decision before Wallet moves money.", "For a customer cancellation I first verify ownership, CONFIRMED state and that every visit is still ASSIGNED. The policy chooses an early or late fee, halves it for active Plus, caps it at the charge, and persists both fee and refund in Booking. A platform cancellation is allowed before service start and always requests the full amount. Wallet consumes the fact idempotently, derives wallet/online refund sources from the captured payment, retains any customer fee from online funds first, and creates a durable refund. Provider work happens after the database claim, with the refund UUID used as idempotency reference."),
        ("Booking expiry", "booking-expiry.png", "Figure 8. Scheduled expiry releases scarce capacity and compensates adjacent workflows.", "The scheduler finds bounded batches of expired offer and hold IDs. The processor reloads each booking/row under lock, expires every related offer/hold, changes the booking once, and appends WorkerOfferExpired or BookingExpired. User releases a reserved Plus pass. Wallet fails and releases a pending payment; if money has already arrived through a race, its late-capture path queues a compensation refund."),
        ("Plus subscription", "plus.png", "Figure 9. Provider-paid Plus purchase and pass lifecycle.", "The customer chooses only a plan code. User resolves server-owned price, duration, pass count and discount, then Wallet creates the provider order. A verified capture emits PlusSubscriptionPurchased without crediting wallet balance. User idempotently activates or extends the subscription and grants passes. During matching I reserve at most one eligible pass and prevent a discount from reducing a booking below one rupee. Booking facts consume, release or restore the pass; a reconciler repairs a reservation left by a crash."),
        ("Notifications", "notifications.png", "Figure 10. Transactional outbox, Kafka dedupe and leased provider delivery.", "Booking and Wallet facts are consumed by Notification through retry-enabled listeners. I deduplicate the event ID, resolve the recipient from authoritative ownership when needed, select a template, and commit notification history and delivery rows together. WorkerOffersCreated fans out one worker notification per offer. The delivery scheduler leases rows, sends push through FCM and adds SMS for critical templates, then marks delivered, retries, or DEAD. WebSocket clients resume strictly after a durable notification ID. Local profiles log masked delivery instead of contacting providers."),
        ("Live support", "support.png", "Figure 11. Booking-bound support uses durable messages and resumable SSE.", "A customer can open a ticket only for a booking that Booking says they own. Support stores one ticket per booking and the initial message in the same local operation; re-opening replays the existing ticket. Customer and staff messages have durable increasing IDs, and SSE resumes after a caller-provided message cursor. Staff can list, stream, reply and resolve. This is an in-app durable chat—not a third-party help-desk or bot integration."),
    ]
    for title, fig, cap, narrative in workflows:
        add_heading(doc, title, 2)
        add_figure(doc, fig, cap, 7.0)
        add_body(doc, narrative)

    add_heading(doc, "3. Service chapters", 1)
    service_text = {
        "API Gateway": ("I keep the edge intentionally thin. It verifies HS256 JWTs, checks the configured issuer, maps route families to CUSTOMER, WORKER, SUPPORT_AGENT, OPERATIONS or FINANCE, strips untrusted identity headers and writes trusted subject/role headers. A Redis token bucket is keyed by authenticated subject or direct address. Public exceptions are OTP routes, health and the Razorpay webhook. It stores no business data and emits no Kafka events.", "Redis only for rate limiting; no relational tables", "Routes to all seven services", "JWT at the edge; deny-by-default route rules"),
        "User Service": ("I make User authoritative for registered customer/staff identity, verified Mumbai addresses, normalized household constraints, favourite ordering, Plus subscriptions/passes and customer ratings. It publishes user facts and consumes booking/wallet facts only for Plus lifecycle. Address verification can use Google forward/reverse geocoding; disabled local mode performs the local boundary checks but not provider corroboration.", "User PostgreSQL", "HTTP to Wallet/Booking; Kafka from Booking/Wallet; publishes user-events", "Customer/staff OTP plus internal service-token filter"),
        "Booking Service": ("I use Booking as the conductor and the only owner of booking state. It owns the catalogue, atomic slot holds, offers, payment-state transition, worker job progression, cancellation decision and privacy window for tracking. It reads User and Worker synchronously, asks Wallet to start payment, consumes wallet results and publishes booking facts.", "Booking PostgreSQL", "HTTP to User/Worker/Wallet; consumes wallet-events; publishes booking-events", "Customer/worker/staff identities from gateway; service token internally"),
        "Worker Service": ("I isolate worker-owned skills, availability, compatibility, tier, rating and location. Matching is deterministic: hard filters first, then favourite/Plus, rating, distance and UUID tie-break. Pricing is base hourly rate × whole hours plus one per-visit tier surcharge: Standard ₹0, Veteran ₹30, Expert ₹50.", "Worker PostgreSQL", "Internal match/location/contact APIs; publishes worker-events", "Worker OTP/JWT and internal service token"),
        "Wallet Service": ("I make Wallet the only money owner. Balances are derived from an append-only ledger and reservations. Top-ups, Plus purchases, booking splits, provider callbacks and refunds are explicit state machines with idempotency keys and row locks. Booking supplies the authoritative charge; finance can retry only an existing durable refund.", "Wallet PostgreSQL", "HTTP to Booking quote; consumes booking-events; publishes wallet-events; Razorpay API/webhook", "Customer/staff at gateway; provider signature; internal service token"),
        "Support Service": ("I implement durable booking-bound live chat with customer ownership checks and staff operations. It uses coroutines/Flow for SSE polling over committed message IDs. It currently has no Kafka integration and no external support-provider integration.", "Support PostgreSQL", "Internal HTTP to Booking ownership", "Customer/staff roles plus reactive internal service-token filter"),
        "Notification Service": ("I turn selected Booking and Wallet facts into customer/worker history and delivery jobs. Recipient type is explicit, histories are isolated, and reconnecting WebSockets use durable IDs. Delivery is leased for horizontal workers; production adapters support FCM HTTP v1 and Twilio, while local adapters log masked data.", "Notification PostgreSQL", "Consumes booking-events and wallet-events; HTTP contact/ownership lookups to User/Worker/Booking/Wallet", "Customer/worker identities plus reactive internal service-token filter"),
        "Platform Common": ("I centralize only cross-cutting deployment safeguards and the SMS provider adapter. The production guard fails startup for missing/placeholder secrets, disabled production providers or non-live Razorpay mode. I deliberately do not put domain entities or shared business exceptions here.", "No database", "Twilio provider HTTP only", "Configuration validation at startup"),
    }
    for name, (narrative, data, comms, security) in service_text.items():
        add_heading(doc, name, 2)
        add_body(doc, narrative)
        add_table(doc, ["Aspect", "Implementation"], [("Owned data", data),("Communication", comms),("Security", security)], [1.3,5.7])
        if name in DBS:
            add_heading(doc, "Owned tables", 3)
            add_table(doc, ["Table", "Key responsibility"], DBS[name], [2.2,4.8])

    add_heading(doc, "4. API reference", 1)
    add_body(doc, "I list the implemented HTTP/WebSocket surface below. Field names are the Kotlin/Jackson names; money is in minor units. The gateway injects authenticated identity headers, so bodies do not choose the acting customer, worker or staff member.")
    for service, rows in APIS.items():
        add_heading(doc, service, 2)
        add_table(doc, ["Method", "Path", "Caller", "Request", "Response", "Behavior", "Main errors"], rows, [.55,1.55,.8,1.1,1.05,1.6,.8])

    add_heading(doc, "5. Method-level logic", 1)
    add_body(doc, "This chapter is generated from every non-DTO, non-exception Kotlin source file in the repository. I show each public method signature and its responsibility. Constructors, configuration-only properties and trivial private mapping/validation helpers are grouped with their class rather than repeated as standalone methods.")
    service_order = ["api-gateway","user-service","booking-service","worker-service","wallet-service","support-service","notification-service","platform-common"]
    for service in service_order:
        add_heading(doc, service, 2)
        for path, classes, methods in class_inventory(service):
            title = " / ".join(classes)
            add_heading(doc, title, 3)
            p = doc.add_paragraph(path)
            p.paragraph_format.keep_with_next = True
            p.runs[0].font.name = "Consolas"; p.runs[0].font.size = Pt(7.5); p.runs[0].font.color.rgb = RGBColor(102,112,133)
            if not methods:
                add_body(doc, f"I use this {('domain/configuration component' if '/domain/' in path or '/config/' in path else 'component')} as the named responsibility indicates. It exposes no public Kotlin method beyond construction or framework callbacks visible through interfaces/annotations.")
            else:
                rows = []
                for sig in methods:
                    rows.append((sig, method_explanation(classes[0], sig, path)))
                add_table(doc, ["Public method", "What I do"], rows, [2.45,4.55])

    add_heading(doc, "6. Cross-cutting design", 1)
    cross = [
        ("Transactional outbox and idempotency", "I write business state and outbox_event in one local transaction. Publishers claim rows with a lease, send an envelope containing eventId, aggregateId, eventType, occurredAt, schemaVersion and payload, and acknowledge only after Kafka completes. Failure records a retry time and error. Kafka keys are aggregate IDs, preserving order per booking/wallet/user/worker. Consumers insert (consumer_name, eventId) in processed_message in the same transaction as their side effect. Spring retry topics make four bounded attempts and route exhausted records to a -dlt topic. An absent schemaVersion is treated as version 1 for rolling upgrades; explicit future versions fail."),
        ("Wallet ledger rules", "I calculate ledger balance as CREDIT minus DEBIT, available balance as ledger balance minus non-expired ACTIVE reservations, and total spent from booking-payment DEBIT entries. Ledger writes carry unique idempotency keys and are never updated into a new balance. A reservation is captured before its DEBIT is appended. Refunds create new CREDIT rows; history remains auditable. Plus purchases are provider payments but not wallet credits."),
        ("Concurrency and double-booking prevention", "I sort distinct worker/date keys before locking worker_day_capacity rows with PESSIMISTIC_WRITE, then check existing HELD/CONFIRMED overlaps. Every visit in a multi-day booking is created in one transaction, so a failure rolls back all visits. The transaction commits before worker/User/Wallet/provider work continues. Booking, offer, payment, wallet, pass, device and refund transitions use targeted row locks; I never use Kotlin synchronized as a distributed lock."),
        ("Retries, late events and dead letters", "Outbox leases and delivery/refund processing leases allow another instance to recover after a crash. A webhook that arrives before an order is attached stays in the processed_webhook inbox as pending and a scheduler replays it. A capture after payment cancellation creates a durable compensation refund. Provider order creation is reconciled by exact deterministic receipt. Failed Kafka records use retry topics and DLT handlers; delivery jobs stop at DEAD after the configured attempt count."),
        ("Service-to-service authentication", "Every /internal/v1 route checks X-Service-Token, and the shared RestClient factory automatically sends the configured value. The gateway strips client-supplied identity headers and injects the JWT subject/role. This is adequate for a single-server prototype but the shared bearer token is a known production risk; I would replace it with workload identity or mTLS and short-lived credentials."),
        ("Error handling", "Each service maps domain exceptions through RestControllerAdvice to stable ProblemDetail HTTP responses. Controllers remain thin and do not leak provider bodies or credentials. Network adapters translate provider/service failures into service-specific unavailable/rejected errors, while invalid lifecycle transitions remain conflicts rather than silent rewrites."),
        ("Production configuration guard", "platform-common runs a startup guard in production. It rejects missing or known development values for JWT, OTP pepper, service token and database/provider configuration; requires live Razorpay mode; and refuses disabled geocoding/SMS/push providers. This turns dangerous defaults into a startup failure rather than a hidden degraded production mode."),
    ]
    for heading, body in cross:
        add_heading(doc, heading, 2); add_body(doc, body)
    add_code(doc, "availableMinor = sum(CREDIT) - sum(DEBIT) - sum(active reservations)\ntotalSpentMinor = sum(completed booking-payment DEBIT entries)")

    add_heading(doc, "7. Data and events reference", 1)
    add_heading(doc, "Kafka topics and payloads", 2)
    add_body(doc, "All implemented producer envelopes use schemaVersion 1. Payloads intentionally exclude addresses, phone numbers, payment credentials and raw webhook bodies.")
    add_table(doc, ["Topic", "Event type", "Payload fields", "Implemented consumers/status"], EVENTS, [1.0,1.65,2.7,1.65])
    add_heading(doc, "Database tables by service", 2)
    for service, rows in DBS.items():
        add_heading(doc, service, 3)
        add_table(doc, ["Table", "Purpose/key columns"], rows, [2.2,4.8])

    add_heading(doc, "8. Deployment and operations", 1)
    add_heading(doc, "Local full-stack startup", 2)
    add_body(doc, "The repository has a multi-stage Java 21 Dockerfile. The base compose.yaml starts Kafka, Redis and six PostgreSQL instances; compose.apps.yaml adds all seven deployable services and health dependencies. Host bindings use 127.0.0.1, and application images run as a non-root user. The gateway is on 8080; direct service ports 8081–8086 are for local diagnostics.")
    add_code(doc, "docker compose -f compose.yaml -f compose.apps.yaml up --build -d\ndocker compose -f compose.yaml -f compose.apps.yaml ps\ncurl http://127.0.0.1:8080/actuator/health\ndocker compose -f compose.yaml -f compose.apps.yaml down")
    add_heading(doc, "Single-server production shape", 2)
    add_body(doc, "I expose only the gateway behind a TLS reverse proxy. Services, Kafka, Redis and PostgreSQL stay on private networking. Each state owner receives a distinct database URL/user/password and Flyway migrates only its own schema. Production should add managed backups, restore drills, monitoring/alerts, log aggregation, secret management, broker replication and rolling deployment policy; the repository documents the topology but does not provision that infrastructure.")
    add_heading(doc, "Configuration variables (names only)", 2)
    envs = [
        "KAFKA_BOOTSTRAP_SERVERS", "REDIS_HOST", "REDIS_PORT", "JWT_SECRET", "AUTH_ISSUER", "OTP_PEPPER", "SERVICE_AUTH_TOKEN",
        "USER_SERVICE_URL", "BOOKING_SERVICE_URL", "WORKER_SERVICE_URL", "WALLET_SERVICE_URL", "SUPPORT_SERVICE_URL", "NOTIFICATION_SERVICE_URL",
        "USER_DB_URL / USER_DB_USERNAME / USER_DB_PASSWORD", "BOOKING_DB_URL / BOOKING_DB_USERNAME / BOOKING_DB_PASSWORD", "WORKER_DB_URL / WORKER_DB_USERNAME / WORKER_DB_PASSWORD",
        "WALLET_DB_URL / WALLET_DB_USERNAME / WALLET_DB_PASSWORD", "SUPPORT_DB_URL / SUPPORT_DB_USERNAME / SUPPORT_DB_PASSWORD", "NOTIFICATION_DB_URL / NOTIFICATION_DB_USERNAME / NOTIFICATION_DB_PASSWORD",
        "RAZORPAY_MODE", "RAZORPAY_KEY_ID", "RAZORPAY_KEY_SECRET", "RAZORPAY_WEBHOOK_SECRET", "RAZORPAY_BASE_URL",
        "GEOCODING_PROVIDER", "GEOCODING_API_KEY", "SMS_PROVIDER", "TWILIO_ACCOUNT_SID", "TWILIO_AUTH_TOKEN", "TWILIO_FROM_NUMBER",
        "PUSH_PROVIDER", "FCM_PROJECT_ID", "FCM_SERVICE_ACCOUNT_JSON", "SPRING_PROFILES_ACTIVE",
    ]
    add_table(doc, ["Environment variable name"], [(x,) for x in envs], [7.0])
    add_heading(doc, "Health and operational checks", 2)
    add_bullets(doc, [
        "Actuator health is exposed through the gateway and directly on each local service port.",
        "docker compose ps and service logs show dependency readiness and migration/startup failures.",
        "The build gate is ./gradlew test bootJar --no-configuration-cache --rerun-tasks; Docker-backed Testcontainers tests require Docker and CI rejects skips.",
        "Outbox unpublished/lease age, DLT volume, refund age, webhook pending age, notification DEAD rows, support backlog and gateway 429 rate are the first production dashboards I would add.",
    ])

    add_heading(doc, "9. Honest status and next steps", 1)
    add_heading(doc, "Complete in the prototype", 2)
    add_bullets(doc, [
        "Seven deployable Spring Boot services, six isolated PostgreSQL databases, Redis, Kafka, gateway routing and container topology.",
        "OTP/JWT authentication for seeded customers, workers and staff; role-separated gateway authorization and trusted identity headers.",
        "Verified-address model, full household compatibility, worker schedules/matching/tier pricing, three favourites and mutual per-visit ratings.",
        "Instant, scheduled and all-or-nothing multi-day booking; atomic holds; offers; payment confirmation; job lifecycle; location privacy and arrival checks.",
        "Append-only wallet ledger, wallet reservations, split payment, signed capture webhook, Plus purchase, cancellation refund, durable provider reconciliation and finance retry audit.",
        "Transactional outboxes for Booking/Wallet/User/Worker, versioned envelopes, idempotent consumers, retries/DLTs, durable notifications and support chat.",
    ])
    add_heading(doc, "Mocked, local-mode or partial", 2)
    add_table(doc, ["Area", "Current truth"], [
        ("Registration/profile onboarding", "Not exposed. Prototype identities are seeded; OTP is login, not signup."),
        ("Razorpay", "Real adapter and webhook verification exist. Local default can use an in-memory fake order/refund provider; production guard requires live mode."),
        ("SMS/push", "Twilio and FCM HTTP v1 adapters exist. Local/test modes use logging adapters and masked/deterministic delivery."),
        ("Geocoding", "Google forward/reverse adapter exists. Disabled local mode does not provide third-party address corroboration."),
        ("Notifications", "Consumes booking-events and wallet-events only. user-events and worker-events are published but have no in-repo consumers."),
        ("Worker rejection", "Releases the atomic booking. Automatic re-match/escalation is not implemented."),
        ("Support", "Durable in-app SSE chat exists; no WhatsApp, bot, agent assignment/SLA or external help-desk integration."),
        ("Internal trust", "A shared static service token protects internal HTTP. Workload identity, mTLS and token rotation are not implemented."),
        ("Operations", "Health endpoints and production guard exist. Metrics dashboards, tracing, alert rules, backup automation and deployment manifests are not provisioned."),
    ], [1.5,5.5])
    add_heading(doc, "Known risks", 2)
    add_bullets(doc, [
        "HS256 and a shared internal token concentrate trust in static secrets; rotation and least-privilege identities are production prerequisites.",
        "Single-server Kafka/PostgreSQL/Redis topology is not highly available and needs backups plus tested restores.",
        "Polling-based SSE/WebSocket and delivery schedulers are appropriate for the prototype but need capacity tests and operational limits before large scale.",
        "Event payload compatibility is versioned but there is no schema registry or automated consumer-driven contract deployment gate.",
        "No worker payout, settlement, tax invoice, fraud/risk, dispute, cancellation escalation, analytics warehouse or admin UI exists.",
    ])
    add_heading(doc, "Next steps I would take", 2)
    add_bullets(doc, [
        "Provision real provider accounts in a secrets manager and complete sandbox-to-live end-to-end certification, including webhook replay and refund ambiguity drills.",
        "Replace the shared service token with workload identity or mTLS and introduce key/token rotation.",
        "Add OpenTelemetry traces, business metrics and actionable alerts for outbox, DLT, refunds, webhooks, delivery and slot contention.",
        "Add registration/onboarding, worker compliance/operations, payout/settlement and a safe administrative interface.",
        "Define re-matching/escalation after worker rejection and formalize notification coverage for user-events and worker-events.",
        "Run load, chaos, backup/restore and rolling-deployment tests before launch; keep the existing concurrency and money tests as release gates.",
    ])

    add_heading(doc, "Appendix A. Test evidence", 1)
    add_body(doc, "I use unit tests for rules and state machines, and Testcontainers integration tests for the database behaviors that matter under concurrency. The repository contains 70 Kotlin test files. The strongest evidence includes:")
    test_groups = [
        ("Booking", "window rules; service catalogue; all-or-nothing multi-day creation; concurrent slot hold winner; idempotency replay; offer lifecycle; payment expiry; cancellation; 200 m arrival; privacy-filtered feeds and live tracking"),
        ("Wallet", "top-up webhook idempotency; split payment; concurrent overspend prevention; invalid signature rejection; late-capture compensation; refund lease recovery; exact receipt recovery; provider refund idempotency and finance audit"),
        ("User/Plus", "OTP cooldown/attempts; verified address persistence; maximum favourites; Plus pass reserve/consume/release/restore/reconcile; server-owned plan terms; mutual rating authorization"),
        ("Worker", "OTP; availability ownership/ranges; complete compatibility filters; location freshness/accuracy; rating authorization; worker outbox"),
        ("Notification/Support/Gateway", "event dedupe and worker fan-out; atomic notification+delivery persistence; FCM request; delivery retry; durable stream cursors; internal filters; role routing, identity header replacement and rate-limit keys"),
        ("Platform", "production guard rejects defaults/disabled providers/non-live Razorpay; Twilio adapter request"),
    ]
    add_table(doc, ["Area", "What the tests assert"], test_groups, [1.25,5.75])

    for section in doc.sections:
        footer = section.footer.paragraphs[0]
        footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
        run = footer.add_run("Snabbit Backend Walkthrough  •  code-grounded prototype")
        run.font.size = Pt(8); run.font.color.rgb = RGBColor(102,112,133)
        fld = OxmlElement("w:fldSimple"); fld.set(qn("w:instr"), "PAGE")
        footer._p.append(fld)
    doc.core_properties.title = "Snabbit Backend Walkthrough"
    doc.core_properties.subject = "Technical walkthrough of the Kotlin/Spring Boot backend"
    doc.core_properties.author = "Snabbit backend team"
    doc.save(OUT)


def build_source_summary():
    lines = ["# Snabbit Backend Walkthrough — source manifest", "", "This Markdown file is the reviewable source manifest for the generated Word document. The full authored prose, tables, diagrams and code-derived method inventory live in `build_snabbit_walkthrough.py`; rerun that script with the bundled document runtime to reproduce the DOCX.", "", "## Generated deliverables", "", "- `Snabbit-Backend-Walkthrough.docx`", "- `walkthrough-assets/*.png` (architecture and sequence diagrams)", "", "## Code evidence read by the generator", ""]
    for service in ["api-gateway","user-service","booking-service","worker-service","wallet-service","support-service","notification-service","platform-common"]:
        main = len(list((ROOT/service/"src/main/kotlin").rglob("*.kt")))
        tests = len(list((ROOT/service/"src/test/kotlin").rglob("*.kt"))) if (ROOT/service/"src/test/kotlin").exists() else 0
        lines.append(f"- **{service}**: {main} main Kotlin files; {tests} test files")
    lines += ["", "## Repository sources also reviewed", "", "- Root Gradle settings/build files, Dockerfile, compose.yaml, compose.apps.yaml, .env.example and .dockerignore", "- README.md, DEPLOY.md and every existing Markdown design/API/operations document under docs/", "- Every Flyway migration under each state-owning service", "- All Kotlin tests, including Testcontainers concurrency, ledger, webhook and persistence suites", "", "## Rebuild", "", "```powershell", "& $BUNDLED_PYTHON docs/build_snabbit_walkthrough.py", "```", "", "The document intentionally contains environment variable names only and excludes secrets, tokens, OTPs, phone numbers and provider credentials."]
    SOURCE.write_text("\n".join(lines)+"\n", encoding="utf-8")


if __name__ == "__main__":
    DOCS.mkdir(exist_ok=True)
    build_doc()
    build_source_summary()
    print(OUT)
    print(SOURCE)
