/*
   HeadlessGState.h

   Copyright (C) 2004 Free Software Foundation, Inc.

   Based on work by: Banlu Kemiyatorn <object at gmail dot com>

   This file is part of GNUstep.

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

#ifndef HeadlessGState_h
#define HeadlessGState_h

#include "gsc/GSGState.h"

@interface HeadlessGState : GSGState
{
  /* Held so the DPScurrent... accessors answer what was set.  Nothing is
     drawn, but a caller that sets a parameter and reads it back gets its own
     value rather than an exception.  Initialised to the PostScript defaults. */
  int _linecap;
  int _linejoin;
  int _strokeadjust;
  CGFloat _linewidth;
  CGFloat _miterlimit;
}
@end

#endif /* HeadlessGState_h */