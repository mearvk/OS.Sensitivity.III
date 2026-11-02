/* Coverage for the drawing primitives HeadlessGState implements
 * (Source/headless/HeadlessGState.m).
 *
 * GSGState declares 26 methods as subclassResponsibility, which raise
 * NSInvalidArgumentException unless the graphics backend overrides them.  The
 * headless backend draws nothing, so each of them has to return rather than
 * raise: a caller cannot otherwise tell a primitive that does nothing from one
 * that aborts the drawing.
 *
 * The line parameters are the part with observable state.  They are held so
 * that the DPScurrent... accessors answer what was set, and they start at the
 * PostScript defaults, so a read before any write gives 1, 0, 0, 10 and 0
 * rather than zero.
 *
 * A backend test can only build against the backend it belongs to, so this
 * guards on the headless graphics backend actually being built (config.h names
 * it through BUILD_GRAPHICS) and skips cleanly on every other one.
 */
#import <Foundation/Foundation.h>
#import "Testing.h"
#include "config.h"

#if defined(BUILD_GRAPHICS) && defined(GRAPHICS_headless) \
  && BUILD_GRAPHICS == GRAPHICS_headless

#import <AppKit/AppKit.h>

int
main(void)
{
  START_SET("headless gstate primitives")

  NSGraphicsContext *ctxt = nil;
  CGFloat width = -1.0;
  CGFloat miter = -1.0;
  int cap = -1;
  int join = -1;
  int adjust = -1;

  /* Loading the backend raises when it cannot be found; treat that as
   * nothing to test here rather than a failure. */
  NS_DURING
    {
      [NSApplication sharedApplication];
      ctxt = [NSGraphicsContext currentContext];
    }
  NS_HANDLER
    {
      if ([[localException name] isEqualToString: @"SkipSet"])
	[localException raise];
      ctxt = nil;
    }
  NS_ENDHANDLER

  if (ctxt == nil)
    {
      SKIP("the headless backend is not available")
    }
  else
    {
      [ctxt DPScurrentlinewidth: &width];
      [ctxt DPScurrentlinecap: &cap];
      [ctxt DPScurrentlinejoin: &join];
      [ctxt DPScurrentmiterlimit: &miter];
      [ctxt DPScurrentstrokeadjust: &adjust];
      PASS(width == 1.0 && cap == 0 && join == 0 && miter == 10.0
	&& adjust == 0,
	"the line parameters start at the PostScript defaults");

      [ctxt DPSsetlinewidth: 4.5];
      [ctxt DPSsetlinecap: 2];
      [ctxt DPSsetlinejoin: 1];
      [ctxt DPSsetmiterlimit: 3.25];
      [ctxt DPSsetstrokeadjust: 1];

      width = -1.0; miter = -1.0; cap = -1; join = -1; adjust = -1;
      [ctxt DPScurrentlinewidth: &width];
      [ctxt DPScurrentlinecap: &cap];
      [ctxt DPScurrentlinejoin: &join];
      [ctxt DPScurrentmiterlimit: &miter];
      [ctxt DPScurrentstrokeadjust: &adjust];
      PASS(width == 4.5 && cap == 2 && join == 1 && miter == 3.25
	&& adjust == 1,
	"a line parameter that is set reads back");

      PASS_RUNS(({
	  [ctxt DPSinitclip];
	  [ctxt DPSeoclip];
	  [ctxt DPSeofill];
	  [ctxt DPSfill];
	  [ctxt DPSstroke];
	}),
	"the clip and fill primitives return without drawing");

      PASS_RUNS(({
	  [ctxt DPSshow: "text"];
	}),
	"the text primitive returns without drawing");
    }

  END_SET("headless gstate primitives")
  return 0;
}

#else

int
main(void)
{
  START_SET("headless gstate primitives")
    SKIP("back is not built with the headless graphics backend")
  END_SET("headless gstate primitives")
  return 0;
}

#endif
