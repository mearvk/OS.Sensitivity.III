/*
   HeadlessGState.m

   Copyright (C) 2003, 2023 Free Software Foundation, Inc.

   Based on work by: Marcian Lytwyn <gnustep@advcsi.com> for Keysight
   Based on work by: Banlu Kemiyatorn <object at gmail dot com>
   Based on work by: Fred Kiefer <fredkiefer@gmx.de>

   This library is free software; you can redistribute it and/or
   modify it under the terms of the GNU Lesser General Public
   License as published by the Free Software Foundation; either
   version 2 of the License, or (at your option) any later version.

   This library is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	 See the GNU
   Lesser General Public License for more details.

   You should have received a copy of the GNU Lesser General Public
   License along with this library; see the file COPYING.LIB.
   If not, see <http://www.gnu.org/licenses/> or write to the 
   Free Software Foundation, 51 Franklin Street, Fifth Floor, 
   Boston, MA 02110-1301, USA.
*/

#include "headless/HeadlessGState.h"

@implementation HeadlessGState

- (id) copyWithZone: (NSZone *)zone
{
  HeadlessGState *copy = (HeadlessGState *)[super copyWithZone: zone];

  copy->_linecap = _linecap;
  copy->_linejoin = _linejoin;
  copy->_strokeadjust = _strokeadjust;
  copy->_linewidth = _linewidth;
  copy->_miterlimit = _miterlimit;
  return copy;
}

- (id) initWithDrawContext: (GSContext *)drawContext
{
  self = [super initWithDrawContext: drawContext];
  if (self != nil)
    {
      /* The PostScript defaults, so a read before any write is not zero. */
      _linecap = 0;
      _linejoin = 0;
      _strokeadjust = 0;
      _linewidth = 1.0;
      _miterlimit = 10.0;
    }
  return self;
}

- (void) DPSclip
{
}

- (void) DPSeoclip
{
}

- (void) DPSinitclip
{
}

- (void) DPSfill
{
}

- (void) DPSeofill
{
}

- (void) _paintPath: (ctxt_object_t)drawType
{
}

- (void *) saveClip
{
  return NULL;
}

- (void) restoreClip: (void *)savedClip
{
}

- (void) DPSshow: (const char *)s
{
}

- (void) GSShowText: (const char *)string : (size_t)length
{
}

- (void) GSShowGlyphsWithAdvances: (const NSGlyph *)glyphs
                                 : (const NSSize *)advances
                                 : (size_t)length
{
}

- (void) DPSsetlinewidth: (CGFloat)width
{
  _linewidth = width;
}

- (void) DPScurrentlinewidth: (CGFloat *)width
{
  if (width != NULL)
    {
      *width = _linewidth;
    }
}

- (void) DPSsetlinecap: (int)linecap
{
  _linecap = linecap;
}

- (void) DPScurrentlinecap: (int *)linecap
{
  if (linecap != NULL)
    {
      *linecap = _linecap;
    }
}

- (void) DPSsetlinejoin: (int)linejoin
{
  _linejoin = linejoin;
}

- (void) DPScurrentlinejoin: (int *)linejoin
{
  if (linejoin != NULL)
    {
      *linejoin = _linejoin;
    }
}

- (void) DPSsetmiterlimit: (CGFloat)limit
{
  _miterlimit = limit;
}

- (void) DPScurrentmiterlimit: (CGFloat *)limit
{
  if (limit != NULL)
    {
      *limit = _miterlimit;
    }
}

- (void) DPSsetstrokeadjust: (int)b
{
  _strokeadjust = b;
}

- (void) DPScurrentstrokeadjust: (int *)b
{
  if (b != NULL)
    {
      *b = _strokeadjust;
    }
}

- (void) DPSsetdash: (const CGFloat *)pat : (NSInteger)size : (CGFloat)foffset
{
}

- (void) DPSimage: (NSAffineTransform *)matrix : (NSInteger)pixelsWide
		 : (NSInteger)pixelsHigh : (NSInteger)bitsPerSample
		 : (NSInteger)samplesPerPixel : (NSInteger)bitsPerPixel
		 : (NSInteger)bytesPerRow : (BOOL)isPlanar
		 : (BOOL)hasAlpha : (NSString *)colorSpaceName
		 : (const unsigned char *const[5])data
{
}

- (void) DPSstroke
{
}

- (void) compositerect: (NSRect)aRect op: (NSCompositingOperation)op
{
}

- (void) compositeGState: (HeadlessGState *)source
		fromRect: (NSRect)srcRect
		 toPoint: (NSPoint)destPoint
		      op: (NSCompositingOperation)op
		fraction: (CGFloat)delta
{
}

@end
