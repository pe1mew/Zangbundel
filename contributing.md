# Contributing Guidelines

Thank you for considering contributing to Zangbundel. The project is in its design phase: the specifications under `design/` are the product until the app exists, and most contributions right now are reviews and improvements of those documents.

## What to contribute, and where

| You want to | Do this |
|---|---|
| Question or improve a requirement | Open an issue or a pull request against `design/functional-requirements.md`. Quote the requirement ID. |
| Answer one of the open questions at the end of a design document | Open an issue with your reasoning or, better, measurements from real devices (BLE connection limits, advertising-set counts, battery). |
| Work on the BLE protocol or the song format | Read `design/functional-requirements.md` first; the other documents state the FRD version they depend on. |
| Contribute songs or lyrics | **Not here.** This repository contains no lyrics and does not accept any. Songs go in a library repository in the format of `design/song-format.md`, with their own licence metadata. |
| Start on the Android app | Read `design/architecture.md` (toolkit, modules, allowed dependencies) and open an issue before code lands in `android/`. |

Discussion in English or Dutch is welcome; the documents themselves are in English, with Dutch UI terms in parentheses.

## Getting started

1. **Fork the repository**: click "Fork" on the repository's page.
2. **Clone your fork**:
   ```sh
   git clone https://github.com/<your-user>/Zangbundel.git
   ```
   The upstream repository is `https://github.com/pe1mew/Zangbundel.git`.
3. **Create a branch** with a descriptive name:
   ```sh
   git checkout -b design/your-topic
   ```

## Making changes to the design documents

- Requirement IDs (`FR-AREA-nn`, `NFR-AREA-nn`) are never reused or renumbered. Withdraw a requirement by marking it *Withdrawn*; do not delete it.
- A change to the FRD bumps its version in the header table and adds a change-history row. Update `design/ble-protocol.md` and `design/song-format.md` in the same change where they are affected, or state in the pull request that they are behind.
- Keep MUST / SHOULD / MAY as in RFC 2119.
- Run the consistency check before you push:
  ```bash
  bash scripts/check-design.sh
  ```
- Add a line under `[Unreleased]` in `changelog.md`.

## Commit messages

Imperative subject, under 72 characters, naming the document and its new version when a specification changes, for example `BLE protocol v0.2: broadcast-first carousel with repair`. Keep commits focused.

## Submitting a pull request

1. **Push to your fork**:
   ```sh
   git push origin design/your-topic
   ```
2. **Open a pull request** against `main` with a clear description: which requirements or sections change, and why.

## Review process

- Pull requests are reviewed by the maintainer.
- Be open to feedback and make the requested changes.
- Keep your branch up to date with `main` before merging.

## Reporting issues

- Check whether the issue has already been reported.
- For design issues, quote the document, version and section or requirement ID.
- For a contradiction between documents, quote both sentences.

## Working with AI coding agents

`CLAUDE.md` in the repository root is the orientation file for AI coding agents and follows the [agent-ready-projects](https://github.com/ducroq/agent-ready-projects) layout. Agents log problems in `memory/gotcha-log.md` and keep `memory/MEMORY.md` as an index. These files are committed and shared; review changes to them like any other.

## Community standards

- Follow the [Code of Conduct](code_of_conduct.md).
- Be respectful and collaborative.
